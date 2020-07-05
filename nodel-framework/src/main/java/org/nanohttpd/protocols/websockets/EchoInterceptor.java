package org.nanohttpd.protocols.websockets;

import org.nanohttpd.protocols.http.IHTTPSession;

import java.io.IOException;

public class EchoInterceptor extends Interceptor {

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        return new EchoInterceptor.DebugWebSocket(handshake);
    }

    private static class DebugWebSocket extends WebSocket {

        private boolean debug = true;

        public DebugWebSocket(IHTTPSession handshakeRequest) {
            super(handshakeRequest);
        }

        @Override
        protected void onOpen() {
            System.out.println("[onOpen] called");
        }

        @Override
        protected void onClose(CloseCode code, String reason, boolean initiatedByRemote) {
            if (this.debug) {
                System.out.println("C [" + (initiatedByRemote ? "Remote" : "Self") + "] " + (code != null ? code : "UnknownCloseCode[" + code + "]")
                        + (reason != null && !reason.isEmpty() ? ": " + reason : ""));
            }
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            try {
                message.setUnmasked();
                sendFrame(message);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
            if (this.debug) {
                System.out.println("P " + pong);
            }
        }

        @Override
        protected void onException(IOException exception) {
            System.out.println("exception occurred " + exception.getMessage());
        }

        @Override
        protected void debugFrameReceived(WebSocketFrame frame) {
            if (this.debug) {
                System.out.println("R " + frame);
            }
        }

        @Override
        protected void debugFrameSent(WebSocketFrame frame) {
            if (this.debug) {
                System.out.println("S " + frame);
            }
        }
    }
}
