/**
 * Copyright (c) 2020 EmeraldPay, Inc
 * Copyright (c) 2019 ETCDEV GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.emeraldpay.dshackle.quorum

import io.emeraldpay.dshackle.upstream.Head
import io.emeraldpay.dshackle.upstream.Upstream
import io.infinitape.etherjar.rpc.RpcException
import java.util.concurrent.atomic.AtomicReference

class NotLaggingQuorum(val maxLag: Long = 0): CallQuorum {

    private val result: AtomicReference<ByteArray> = AtomicReference()
    private val failed = AtomicReference(false)

    override fun init(head: Head) {
    }

    override fun isResolved(): Boolean {
        return !isFailed() && result.get() != null
    }

    override fun isFailed(): Boolean {
        return failed.get()
    }

    override fun record(response: ByteArray, upstream: Upstream): Boolean {
        val lagging = upstream.getLag() > maxLag
        if (!lagging) {
            result.set(response)
            return true
        }
        return false
    }

    override fun record(error: RpcException, upstream: Upstream) {
        val lagging = upstream.getLag() > maxLag
        if (!lagging && result.get() == null) {
            failed.set(true)
        }
    }

    override fun getResult(): ByteArray {
        return result.get()
    }
}