package io.emeraldpay.moonbeam.libp2p

import io.emeraldpay.moonbeam.proto.Dht
import io.netty.buffer.Unpooled
import org.apache.commons.codec.binary.Hex
import spock.lang.Specification

import java.nio.ByteBuffer

class DhtProtocolSpec extends Specification {

    def "Parse msg"() {
        setup:
        def hex = "0804500942dc020a2212207cb6c93267eb0de94973b10e275b6f1e2fc84164ade341dd5b472072f7de35121208048ba2a89a06767d120804ac11000106767d1208040a0001e106767d1208040a00012506767d1208040a0c027706767d1208040a34031606767d12142900000000000000000000ffff8ba2a89a06767d1208040a0c042b06767d1208040a0c04d106767d1208040a34002106767d1208040a34002b06767d1208040a0001ed06767d1208040a00012d06767d1208040a34005a06767d1208040a0001b106767d1208040a0a0a0106767d1208040a34008306767d1208040a3400a806767d1208040a3400c006767d1208040a3400f806767d1208040a3400f906767d1208040a00011e06767d1208040a34004d06767d1208040a3400c406767d1208040a3400d906767d1208040a3400e806767d1208040a34002e06767d1208040a3400af06767d1208040a3400b206767d1208040a3400b406767d42d2010a221220d7315072e80c5a5ff7e79f816bea933329c5207fec63c69b6918c1493d533179120804330f793f06767d1208047f00000106767d1208040a1264b906767d1214290000000000000000000000000000000106767d1208040a9c013a06767d1208040a9c013306767d1208040a00018806767d1208040a00018606767d120804c0a87f6f06767d1208040a0a0a0106767d1208040a9c013f06767d1208040a9c013b06767d1208040a2c050106767d1208040a9c012d06767d1208040a9c012f06767d1208040a2c060106767d180142aa010a22122020f0d8bb3f2bb222f7ba9367b47aad3a941b582b27e799332b55d7a14e6b894c12080423c54bf706767d1208040a0f000e06767d1208040a0000a406767d1208047f00000106767d1208040ac8400f06767d1214290000000000000000000000000000000106767d1208040a0a0a0106767d1208040a9c013b06767d1208040a00018806767d1208040a00018606767d1208040a9c012d06767d1208040a9c013d06767d180142a8010a221220744bcc715c522090ba4ea1ea438af552dfce6aaef0e1e58856b26dcab8c43e7c12080403e7d6a506767d1208047f00000106767d1208040a0f023806767d1208040a14000606767d1214290000000000000000000000000000000106767d1208040a9c012d06767d1208040a0a0a0106767d1208040a9c012f06767d120804c0a87f6f06767d1208040a9c013206767d1208040a2c060106767d1208040a2c070106767d428a010a221220131f6edeab913fcab2412bb9b96c2f39524a0f216483e736814f534fb5aaa109120804b2ff44f206767d1208047f00000106767d120804ac1ffe1906767d120804ac11000106767d1214290000000000000000000000000000000106767d1208040a9c013d06767d1208040a9c012f06767d1208040a9c013306767d1208040a9c013a06767d4282010a22122081fd9cb27c20c156354a442ff1d45b708ff48578fc30828b91524c2718b7fc4512080422ec46b406767d1208047f00000106767d120804ac1f18a906767d1214290000000000000000000000000000000106767d12142926001f1843e66416f7f4636fbb9e478806767d1208040a9c012d06767d1208040a2c060106767d42620a2212201f02f383aba0cee52ce3e909d5aafabf3ccedfbc0ce2cd4876d4c6d40f3e72751208049b8adc6906767d1208047f00000106767d1214290000000000000000000000000000000106767d1208040a14020106767d1208040a00018606767d42760a22122004ee56ab6445e14e7d0574a1d0aa0d6b56c4a55b00bd0058d79191ab997bb29612080422425f0006767d1208047f00000106767d120804ac12000206767d1208040a9c013206767d1208040a9c013a06767d1208040a9c012d06767d1208040aff841f06767d1208040a9c012f06767d18014280010a2212206b6b266397714ae07faa65442c25180f872a71572480d33fb8f18075a36959d112080423f509d006767d1208040a9c013b06767d1208047f00000106767d120804ac12000206767d1208040aff810706767d1208040a9c013a06767d1208040a9c013306767d1208040a0a0a0106767d1208040a14020106767d180142aa010a221220ee117dd07fc6276afba28dbf885fe60b7898d2f96250a3398e6fc7db156469ab12080423e3a1ae06767d1208040a0f001506767d1208047f00000106767d1208040ac8401006767d1214290000000000000000000000000000000106767d1208040a2c050106767d1208040a9c013f06767d1208040a9c013306767d1208040a9c012f06767d1208040a9c012d06767d1208040a9c013206767d1208040a2c040106767d180142dc020a221220f482a2cf0d044fd8cbf0c5a6dbfcae748a9c566f7ae767044bbb955f69eab90612080423c6729a06767d120a0423c6729a06767edd031208040a0b001b06767d120a040a0b001b06767edd03120a047f00000106767edd031208047f00000106767d120a040a01013506767edd031208040a01013506767d1216290000000000000000000000000000000106767edd031214290000000000000000000000000000000106767d1208040a2c060106767d120a040a2c060106767edd031208040a00018606767d120a040a00018606767edd031208040a0000a406767d120a040a0000a406767edd031208040a9c013b06767d120a040a9c013b06767edd031208040a00018806767d120a040a00018806767edd031208040a9c013206767d120a040a9c013206767edd031208040a2c070106767d120a040a2c070106767edd031208040a9c013f06767d120a040a9c013f06767edd03180142620a221220de5a5df9f9bddfffdf80c0b226997fad1d156e89f812fd75f2099c5e15c8e0a7120804035748c506767d1208047f00000106767d120804ac13000206767d1208040a9c013f06767d1208040a00018606767d1208040a9c013d06767d180142a2010a2212209e0269bfbc6a9d1c8f6f26fdef47d1fb710f28e0a3398ecab38c59efeb1397c91208045fd8ca3706767d1208047f00000106767d120804c0a82a0306767d1214290000000000000000000000000000000106767d1214292a0104f9c010382a000000000000000006767d121429fc6c99a2171af36a8cd0cc6befb78bb406767d1208040a2c050106767d1208040a0000a406767d1208040a9c013b06767d4280010a221220ac884b2df1d1a8c4d52961fc6b91e7140303d11435e5f7cc1db9aafd0472e486120804b953d8d806767d1208047f00000106767d1214290000000000000000000000000000000106767d1208040a0a0a0106767d1208040a9c012d06767d1208040a9c012f06767d1208040a9c013306767d1208040a9c013b06767d42d0020a221220063a308e3c54b3879bdf1d77389524d9b8ef2cb4fe68b2d3f047f7bc0761c2fd120804b9e36ebe06767d1208040a0001b106767d120804c0a8014106767d1208040a00011e06767d1208040a00030906767d1208040a0a0a0106767d1208040a0c042b06767d1208040a00030706767d1208040a00014906767d120804c0a8014206767d1208040a00012706767d1208040a0001aa06767d1208040a0001ef06767d1208040a00018806767d1208040a00014106767d1208040a0000d406767d1208040a0c027706767d1208040a9c012d06767d1208040a2c000106767d1208040a9c012b06767d1208040a2c020106767d1208040a9c012f06767d1208040a2c010106767d1208040a0c04d106767d120804c0a87f6f06767d1208040a2c030106767d1208040a9c013306767d1208048ac545e806767d1208040af402a806767d1208048ac56b6c06767d42b0010a2212209eab8cacb7985f0756c94972a76cf8e369e0c9cd8aa998b3064f63d9430ff83a120804905b73af06767d1208040a03004106767d1208047f00000106767d120804ac13000206767d1208040a00018806767d1208040a9c013f06767d1208040a2c040106767d1208040a9c012d06767d1208040a9c013b06767d1208040a0000a406767d1208040a9c013306767d1208040a00018606767d1208040a9c013206767d1208040a2c050106767d42b4010a221220ee592bbf3e6129d65781c1d27b9e39dbd8ce6e8e4897a76176de65aa202dae9512080433fe4f8706767d1208040a9c013206767d1208047f00000106767d1214290000000000000000000000000000000106767d1208040a00018806767d1208040a00018606767d1208040a9c013f06767d1208040a9c013306767d1208040a9c012d06767d1208040a9c012f06767d1208040a0000a406767d1208040a14020106767d1208040a9c013b06767d1801425a0a221220482c1fa219512ca79904ded3fcd6cd4e2106491dae84e2fecdcc97c37beb9f5a120804c1b0559806767d1208047f00000106767d1214290000000000000000000000000000000106767d120804c0a87f6f06767d180142b0010a221220379004c49927f4a24c5c695472b3c1f4c9d94481eb17a7bb20fb923170104d7c1208042259131706767d1208040a0a0a0106767d1208047f00000106767d120804ac11000206767d1208040a9c013a06767d1208040a9c012d06767d1208040a00018806767d1208040a9c013f06767d1208040a9c013306767d1208040a2c060106767d1208040a00018606767d1208040a2c050106767d1208040a9c013b06767d120804c0a87f6f06767d4282020a221220a0242effb7388bf782a15f03a29f8abd7b469893d39ae3178b7045846efd07b812080412bd53b806767d1208040a9c013206767d1208040a9c013a06767d1208040a2c060106767d1208040a00018606767d1208040a9c013d06767d1208040a0a0a0106767d1208040a9c012d06767d1208040a9c013306767d1208040a9c013b06767d1208040a9c013f06767d1208040a9c012f06767d1208040a2c050106767d1208040a2c030106767d120804c0a87f6f06767d1208040a28014606767d1208040a28014c06767d1208040a2c070106767d1208040a0000a406767d1208040a00018806767d1208040000000006767d1208040a2c040106767d1801"
        def msg = Hex.decodeHex(hex)
        def protocol = new DhtProtocol()
        when:
        Dht.Message act = protocol.parse(ByteBuffer.wrap(msg))
        then:
        act != null
        println(act)
    }
}