package com.nufo.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkErrorTest {
    @Test fun `no DNS, refused connection and no route mean the phone is offline`() {
        assertTrue(UnknownHostException("world.openfoodfacts.org").meansOffline())
        assertTrue(ConnectException("refused").meansOffline())
        assertTrue(NoRouteToHostException().meansOffline())
    }

    @Test fun `a busy server is not offline`() {
        // Open Food Facts answering 503 or 429 must say "server busy", not "connect to the internet".
        assertFalse(ApiException("HTTP 503").meansOffline())
    }

    @Test fun `a slow connection is not offline`() {
        assertFalse(SocketTimeoutException("timeout").meansOffline())
        assertFalse(IOException("canceled").meansOffline())
    }
}
