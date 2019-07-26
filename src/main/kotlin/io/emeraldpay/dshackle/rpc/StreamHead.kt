package io.emeraldpay.dshackle.rpc

import com.google.protobuf.ByteString
import io.emeraldpay.api.proto.BlockchainOuterClass
import io.emeraldpay.dshackle.upstream.ConfiguredUpstreams
import io.emeraldpay.dshackle.upstream.Upstreams
import io.emeraldpay.grpc.Chain
import io.grpc.stub.StreamObserver
import io.infinitape.etherjar.domain.TransactionId
import io.infinitape.etherjar.rpc.json.BlockJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.toFlux
import java.lang.Exception
import java.util.concurrent.ConcurrentLinkedQueue
import javax.annotation.PostConstruct
import kotlin.collections.HashMap

@Service
class StreamHead(
        @Autowired private val upstreams: Upstreams
) {

    private val log = LoggerFactory.getLogger(StreamHead::class.java)
    private val clients = HashMap<Chain, ConcurrentLinkedQueue<StreamSender<BlockchainOuterClass.ChainHead>>>()

    @PostConstruct
    fun init() {
        listOf(Chain.ETHEREUM, Chain.ETHEREUM_CLASSIC, Chain.TESTNET_MORDEN, Chain.TESTNET_KOVAN).forEach { chain ->
            if (upstreams.getUpstream(chain)?.getHead() != null) {
                clients[chain] = ConcurrentLinkedQueue()
                subscribe(chain)
            }
        }
    }

    private fun subscribe(chain: Chain) {
        upstreams.getUpstream(chain)!!.getHead().getFlux()
                .doOnComplete {
                    log.info("Closing streams for ${chain.chainCode}")
                    clients.replace(chain, ConcurrentLinkedQueue())!!.forEach { client ->
                        try {
                            client.stream.onCompleted()
                        } catch (e: Throwable) {}
                    }
                }
                .subscribe { block -> onBlock(chain, block) }
    }

    private fun onBlock(chain: Chain, block: BlockJson<TransactionId>) {
        log.info("New block ${block.number} on ${chain.chainCode}")
        clients[chain]!!.toFlux()
                .subscribe { stream ->
                    notify(chain, block, stream)
                }
    }

    fun add(chain: Chain, client: StreamObserver<BlockchainOuterClass.ChainHead>) {
        val sender = StreamSender(client)
        if (!clients.containsKey(chain)) {
            client.onError(Exception("Chain ${chain.chainCode} is not available for streaming"))
            return
        }
        clients[chain]!!.add(sender)
        process(chain, sender)
    }

    fun process(chain: Chain, client: StreamSender<BlockchainOuterClass.ChainHead>): Boolean {
        val upstream = upstreams.getUpstream(chain) ?: return false
        val head = upstream.getHead().getHead()
        return head.map {
            notify(chain, it, client)
        }.defaultIfEmpty(false).block()!!
    }

    fun notify(chain: Chain, block: BlockJson<TransactionId>, client: StreamSender<BlockchainOuterClass.ChainHead>): Boolean {
        val data = BlockchainOuterClass.ChainHead.newBuilder()
                .setChainValue(chain.id)
                .setHeight(block.number)
                .setTimestamp(block.timestamp.time)
                .setWeight(ByteString.copyFrom(block.totalDifficulty.toByteArray()))
                .setBlockId(block.hash.toHex().substring(2))
                .build()
        var sent: Boolean = false
        try {
            sent = client.send(data)
            if (!sent) {
                clients[chain]!!.remove(client)
            }
        } catch (e: Exception) {
            log.error("Send error ${e.javaClass}: ${e.message}")
        }
        return sent
    }

}