package io.emeraldpay.moonbeam.crawler

import identify.pb.IdentifyOuterClass
import io.emeraldpay.moonbeam.libp2p.*
import io.emeraldpay.moonbeam.monitoring.PrometheusMetric
import io.emeraldpay.moonbeam.monitoring.PrometheusMetric.Companion.reportConnError
import io.emeraldpay.moonbeam.monitoring.PrometheusMetric.Companion.reportProtocolError
import io.emeraldpay.moonbeam.monitoring.PrometheusMetric.Companion.reportConnBytes
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.crypto.PubKey
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.core.multiformats.Protocol
import io.netty.buffer.Unpooled
import io.netty.channel.*
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.netty.Connection
import reactor.netty.NettyInbound
import reactor.netty.NettyOutbound
import reactor.netty.tcp.TcpClient
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import java.io.IOException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.function.Function

class CrawlerClient(
        private val remote: Multiaddr,
        private val agent: IdentifyOuterClass.Identify,
        private val keys: Pair<PrivKey, PubKey>
) {

    companion object {
        private val log = LoggerFactory.getLogger(CrawlerClient::class.java)
    }

    private val multistream = Multistream()

    private var connection: Connection? = null
    private var proDirLabel = PrometheusMetric.Dir.IN

    fun getHost(): String {
        if (remote.has(Protocol.IP4)) {
            return remote.getStringComponent(Protocol.IP4)!!
        }
        if (remote.has(Protocol.DNS4)) {
            return remote.getStringComponent(Protocol.DNS4)!!
        }
        throw IllegalArgumentException("Unsupported address: $remote")
    }

    fun disconnect() {
        log.debug("Disconnecting from $remote")
        connection?.dispose()
    }

    fun connect(): Flux<CrawlerData.Value<*>> {
        log.debug("Connect to $remote")
        val host = getHost()
        val port = remote.getStringComponent(Protocol.TCP)!!.toInt()

        try {
            val results = CompletableFuture<Flux<CrawlerData.Value<*>>>()
            val client = TcpClient.create()
                    .host(host)
                    .port(port)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4000)
                    .option(ChannelOption.AUTO_CLOSE, true)
                    .option(ChannelOption.SO_KEEPALIVE, false)
                    .handle { inbound, outbound ->
                        val result = handle(inbound, outbound, true)
                        results.complete(Flux.from(result.t2))
                        Flux.from(result.t1).onErrorResume { t ->
                            if (t is IOException) {
                                reportConnError(PrometheusMetric.ConnError.IO)
                                log.debug("Failed to connect to $remote")
                            } else {
                                reportConnError(PrometheusMetric.ConnError.INTERNAL)
                                log.warn("Handler failed to start", t)
                            }
                            if (!results.isDone) {
                                results.completeExceptionally(t)
                            }
                            Flux.empty()
                        }
                    }
                    .connect()
                    .doOnNext { conn ->
                        this.connection = conn
                        conn.onDispose().subscribe {
                            log.debug("Disconnected from $remote")
                        }
                    }

            val data = Mono.fromCompletionStage(results)
                    .flatMapMany { it }
                    .onErrorResume(CancellationException::class.java) { Flux.empty<CrawlerData.Value<*>>() }
                    .doFinally {
                        disconnect()
                    }

            return client.thenMany(data)
        } catch (e: ConnectTimeoutException) {
            reportConnError(PrometheusMetric.ConnError.TIMEOUT)
            log.debug("Timeout to connect to $remote")
        } catch (e: IllegalStateException) {
            reportConnError(PrometheusMetric.ConnError.INTERNAL)
            log.debug("Failed to connect to $remote. Message: ${e.message}")
        } catch (e: UnknownHostException) {
            reportConnError(PrometheusMetric.ConnError.IO)
            log.warn("Invalid remote address: $remote")
        } catch (e: Throwable) {
            reportConnError(PrometheusMetric.ConnError.INTERNAL)
            log.error("Unresolved exception ${e.javaClass.name}: ${e.message}")
        } finally {
            disconnect()
        }

        return Flux.empty()
    }

    /**
     * Respond to common requests initialized by Polkadot, such as /ipfs/ping and ipfs/id
     */
    fun respondStandardRequests(mplex: Mplex): Flux<ByteBuffer> {
        return mplex.receiveStreams(object: Mplex.Handler<Flux<ByteBuffer>> {
            override fun handle(stream: Mplex.MplexStream): Flux<ByteBuffer> {
                val repl = Flux.from(stream.inbound)
                        .switchOnFirst { signal, flux ->
                            if (signal.hasValue()) {
                                val msg = String(signal.get()!!.array())
                                        .replace(Regex("[^a-zA-Z0-9./]"), " ")
                                        .replace(Regex("\\s+"), " ")
                                        .trim()
                                if (msg.contains("/ipfs/ping/1.0.0")) {
                                    val start = Mono.just(multistream.multistreamHeader("/ipfs/ping/1.0.0"))
                                    val response = flux.skip(1).take(1).single()
                                    return@switchOnFirst Flux.concat(start, response)
                                } else if (msg.contains("/ipfs/id/1.0.0")) {
                                    val value = ByteBuffer.wrap(agent.toByteArray())
                                    return@switchOnFirst Flux.just(value)
                                } else if (msg.contains("/ipfs/kad/1.0.0")) {
                                    //only accepts DHT, but never replies
                                    val start = Mono.just(multistream.multistreamHeader("/ipfs/kad/1.0.0"))
                                    return@switchOnFirst Flux.concat(start, Mono.never())
                                } else if (msg == "/multistream/1.0.0 ls") {
                                    // see https://github.com/multiformats/multistream-select#listing
                                    val start = Mono.just(multistream.multistreamHeader("ls"))
                                    val protocols = Mono.just(multistream.list(listOf(
                                            "/ipfs/ping/1.0.0",
                                            "/ipfs/kad/1.0.0",
                                            "/ipfs/id/1.0.0",
                                            "/ksmcc3/light/1",
                                            "/substrate/ksmcc3/6"
                                    )))
                                    return@switchOnFirst Flux.concat(start, protocols)
                                } else if (msg.endsWith("/polkadot/1") || msg.endsWith("/polkadot/legacy/1") || msg.endsWith("/paritytech/grandpa/1")) {
                                    return@switchOnFirst Mono.just(multistream.decline())
                                } else {
                                    reportProtocolError(proDirLabel, PrometheusMetric.ProtocolError.MPLEX)
                                    log.debug("Request to an unsupported protocol [$msg] from $remote")
                                    return@switchOnFirst Mono.just(multistream.decline())
                                }
                            }
                            Flux.empty<ByteBuffer>()
                        }
                return stream.send(repl).outbound()
            }
        }).flatMap { it }
    }


    /**
     * Continue connection when both parties are agreed on using Noise. The method sets up the Noise protocol itself
     * and continues with setting up Mplex multiplexing protocol.
     */
    fun handleConnected(secureTransport: NoiseTransport,
                        handleMplex: Function<Flux<ByteBuffer>, Flux<ByteBuffer>>,
                        initiator: Boolean
    ): Function<Flux<ByteBuffer>, Flux<ByteBuffer>> {
        return Function<Flux<ByteBuffer>, Flux<ByteBuffer>> { receiveSource ->
            val receive = receiveSource.share()

            val proposeSecure = Flux.from(secureTransport.handshake())
                    .transform(SizePrefixed.TwoBytes().writer())
            val establishSecure = secureTransport.establish(receive)

            val decrypted =  receive
                    .skipUntil { secureTransport.isEstablished() }
                    .transform(SizePrefixed.TwoBytes().reader())
                    .transform(secureTransport.decoder())
                    .onErrorContinue { t, o ->
                        reportProtocolError(proDirLabel, PrometheusMetric.ProtocolError.NOISE)
                        log.debug("Packet decryption failed", t)
                    }
//                    .transform(DebugCommons.traceByteBuf("decrypted", false))

            val mplex = multistream.withProtocol(initiator, decrypted, "/mplex/6.7.0", handleMplex)

            Flux.concat(
                    proposeSecure,
                    establishSecure,
                    mplex
//                        .transform(DebugCommons.traceByteBuf("encrypt", true))
                        .transform(secureTransport.encoder())
                        .transform(SizePrefixed.TwoBytes().writer())
            ).doOnError { t ->
                reportConnError(PrometheusMetric.ConnError.INTERNAL)
                log.debug("Connection failed: ${t.message}")
            }
        }
    }

    /**
     * Continue with established Mplex transport.
     */
    fun handleMplex(initiator: Boolean): Pair<Function<Flux<ByteBuffer>, Flux<ByteBuffer>>, CompletableFuture<Publisher<CrawlerData.Value<*>>>>  {
        val processorsData = CompletableFuture<Publisher<CrawlerData.Value<*>>>()
        val handler = Function<Flux<ByteBuffer>, Flux<ByteBuffer>> { mplexInbound ->
            val mplex = Mplex(initiator)
            mplex.start(mplexInbound)
            val standardResponses = respondStandardRequests(mplex)

            val identify = RequestIdDetails().requestOne(mplex)
            val dht = RequestDhtDetails().request(mplex)
            val status = RequestStatusDetails().requestOne(mplex)
            val protocols = RequestProtocolsDetails().requestOne(mplex)

            processorsData.complete(
                    Flux.merge(
                            Flux.from(identify.t1).subscribeOn(Schedulers.elastic()),
                            Flux.from(dht.t1).subscribeOn(Schedulers.elastic()),
                            Flux.from(status.t1).subscribeOn(Schedulers.elastic()),
                            Flux.from(protocols.t1).subscribeOn(Schedulers.elastic())
                    )
            )
            val queryOutbound = Flux.merge(
                    identify.t2,
                    dht.t2,
                    status.t2,
                    protocols.t2
            )

            Flux.merge(standardResponses, queryOutbound)
        }
        return Pair(handler, processorsData)
    }


    fun handle(inbound: NettyInbound, outbound: NettyOutbound, initiator: Boolean): Tuple2<Publisher<Void>, Publisher<CrawlerData.Value<*>>> {
        proDirLabel = if (initiator) PrometheusMetric.Dir.OUT else PrometheusMetric.Dir.IN
//        outbound.withConnection { conn ->
//            conn.addHandler(
//                    io.netty.handler.logging.LoggingHandler("io.netty.util.internal.logging.Slf4JLogger",
//                    io.netty.handler.logging.LogLevel.INFO)
//            )
//        }

        val secureTransport = NoiseTransport(keys.first, initiator)

        val inboundBytes = inbound.receive()
                .map {
                    val copy = ByteBuffer.allocate(it.readableBytes())
                    it.readBytes(copy)
                    // it.release()
                    copy.flip()
                }
                .publishOn(Schedulers.elastic())
                .doOnNext {
                    reportConnBytes(proDirLabel, PrometheusMetric.Dir.IN, it.remaining())
                }

        val handleMplex = handleMplex(initiator)

        val connection = multistream.withProtocol(
                initiator,
                inboundBytes, "/noise/ix/25519/chachapoly/sha256/0.1.0",
                handleConnected(secureTransport, handleMplex.first, initiator)
        ).doOnError { t ->
            reportProtocolError(proDirLabel, PrometheusMetric.ProtocolError.NOISE)
            log.debug("Failed to establish Noise: ${t.message}")
        }

        val peerId = secureTransport.getPeerId().map { CrawlerData.Value(CrawlerData.Type.PEER_ID, it) }
        val main: Publisher<Void> = outbound.send(
                connection
                        .doOnNext {
                            reportConnBytes(proDirLabel, PrometheusMetric.Dir.OUT, it.remaining())
                        }
                        .map { Unpooled.wrappedBuffer(it) }
        )

        return Tuples.of(main,
                Flux.merge(
                        Mono.fromCompletionStage(handleMplex.second).flatMapMany { it },
                        peerId
                )
        )
    }

    //
    // ------------------------
    //

    class DataTimeoutException(val name: String): java.lang.Exception("Timeout for $name")

}