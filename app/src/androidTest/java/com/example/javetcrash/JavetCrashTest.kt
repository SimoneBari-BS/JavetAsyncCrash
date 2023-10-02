package com.example.javetcrash

import androidx.annotation.Keep
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.interop.callback.JavetCallbackContext
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.V8ValueString
import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Method
import kotlin.concurrent.thread

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class JavetCrashTest {

    @Test
    fun crashJavet() {
        val runtime: V8Runtime = V8Host.getV8Instance().createV8Runtime()
        val context = runtime.createV8ValueObject().apply {
            val callbackObject = AsyncMethodWrapper(runtime) {
                thread {
                    it.get<V8ValueObject>("simple").use { simpleClass ->
                        check(simpleClass.get<V8ValueString>("string").value == "Hello")
                    }
                    it.get<V8ValueObject>("complex").use { complexClass ->
                        complexClass.get<V8ValueArray>("array").use { array ->
                            array.get<V8ValueObject>(0).use { element ->
                                check(element.get<V8ValueString>("string").value == "World")
                            }
                        }
                    }
                }.join()
                runtime.createV8ValueString("Hello world!")
            }
            val receiver = JavetCallbackContext(callbackObject, callbackObject.invokeMethod)
            this.bindFunction("invoke", receiver)
        }

        runtime.getExecutor(
            """
                function main(context) {
                    var complexClass = {
                        "simple" : {
                          "string": "Hello",
                        },
                        "complex" : {
                            "array" : [{
                              "string": "World",
                            }]
                        }
                    }
                    return context.invoke(complexClass)
                }
            """.trimIndent()
        ).executeVoid()

        repeat(100000) {
            runtime.globalObject.invoke<V8Value>("main", context).use {
                Thread.sleep(1)
            }
        }
    }
}


private class AsyncMethodWrapper(
    private val runtime: V8Runtime,
    private val block: (V8ValueObject) -> V8Value,
) {
    @Keep
    @SuppressWarnings("TooGenericExceptionCaught")
    fun invoke(parameter: V8ValueObject): V8Value {
        val promise = runtime.createV8ValuePromise()
        val jsPromise = promise.promise
        val clonedParam = parameter.toClone<V8ValueObject>()
        thread {
            promise.use {
                val value = clonedParam.use { crisperParam ->
                    block(crisperParam)
                }
                promise.resolve(value)
            }
        }

        return jsPromise
    }

    val invokeMethod
        get(): Method = this.javaClass.getMethod("invoke", V8ValueObject::class.java)
}
