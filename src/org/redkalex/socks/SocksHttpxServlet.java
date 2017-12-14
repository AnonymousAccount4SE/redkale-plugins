/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkalex.socks;

import org.redkale.net.AsyncConnection;
import org.redkale.util.Utility;
import org.redkale.util.AutoLoad;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import org.redkale.util.*;

/**
 * 正向代理
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@AutoLoad(false)
public final class SocksHttpxServlet extends SocksServlet {

    protected static final byte[] LINE = new byte[]{'\r', '\n'};

    @Override
    public void execute(SocksRequest request, SocksResponse response) throws IOException {
        request.getHttpxRequest().setChannel(response.getChannel());
        response.getHttpxResponse().setChannel(response.getChannel());
        execute(request.getHttpxRequest(), response.getHttpxResponse(), request.getContext().getAsynchronousChannelGroup());
    }

    private void execute(HttpxRequest request, HttpxResponse response, final AsynchronousChannelGroup group) throws IOException {
        response.skipHeader();
        if ("CONNECT".equalsIgnoreCase(request.getMethod())) {
            connect(request, response, group);
            return;
        }
        String url = request.getRequestURI();
        url = url.substring(url.indexOf("://") + 3);
        url = url.substring(url.indexOf('/'));
        final ByteBuffer buffer = response.getContext().pollBuffer();
        buffer.put((request.getMethod() + " " + url + " HTTP/1.1\r\n").getBytes());
        for (String header : request.getHeaderNames()) {
            if (!header.startsWith("Proxy-")) {
                buffer.put((header + ": " + request.getHeader(header) + "\r\n").getBytes());
            }
        }
        if (request.getHost() != null) {
            buffer.put(("Host: " + request.getHost() + "\r\n").getBytes());
        }
        if (request.getContentType() != null) {
            buffer.put(("Content-Type: " + request.getContentType() + "\r\n").getBytes());
        }
        if (request.getContentLength() > 0) {
            buffer.put(("Content-Length: " + request.getContentLength() + "\r\n").getBytes());
        }
        buffer.put(LINE);
        ByteArray body = request.getDirectBody();
        if (!body.isEmpty()) buffer.put(body.directBytes(), 0, body.size());
        buffer.put(LINE);
        buffer.flip();
        final AsyncConnection remote = AsyncConnection.createTCP(group, request.getHostSocketAddress(), 6, 6).join();
        remote.write(buffer, null, new CompletionHandler<Integer, Void>() {

            @Override
            public void completed(Integer result, Void attachment) {
                if (buffer.hasRemaining()) {
                    remote.write(buffer, attachment, this);
                    return;
                }
                response.getContext().offerBuffer(buffer);
                new ProxyCompletionHandler(remote, request, response).completed(0, null);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                response.getContext().offerBuffer(buffer);
                response.finish(true);
                try {
                    remote.close();
                } catch (IOException ex) {
                }
            }
        });
    }

    private void connect(HttpxRequest request, HttpxResponse response, final AsynchronousChannelGroup group) throws IOException {
        final InetSocketAddress remoteAddress = request.getURLSocketAddress();
        final AsyncConnection remote = remoteAddress.getPort() == 443
            ? AsyncConnection.create(Utility.createDefaultSSLSocket(remoteAddress)) : AsyncConnection.createTCP(group, remoteAddress, 6, 6).join();
        final ByteBuffer buffer0 = response.getContext().pollBuffer();
        buffer0.put("HTTP/1.1 200 Connection established\r\nConnection: close\r\n\r\n".getBytes());
        buffer0.flip();
        response.sendBody(buffer0, null, new CompletionHandler<Integer, Void>() {

            @Override
            public void completed(Integer result, Void attachment) {
                new ProxyCompletionHandler(remote, request, response).completed(0, null);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                response.finish(true);
                try {
                    remote.close();
                } catch (IOException ex) {
                }
            }
        });

    }

    private static class ProxyCompletionHandler implements CompletionHandler<Integer, Void> {

        private final AsyncConnection remote;

        private final HttpxRequest request;

        private final HttpxResponse response;

        public ProxyCompletionHandler(AsyncConnection remote, HttpxRequest request, HttpxResponse response) {
            this.remote = remote;
            this.request = request;
            this.response = response;
        }

        @Override
        public void completed(Integer result0, Void v0) {
            final ByteBuffer rbuffer = request.getContext().pollBuffer();
            remote.read(rbuffer, null, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    if (result <= 0) {
                        failed(null, attachment); //获取信息完毕
                        return;
                    }
                    rbuffer.flip();
                    CompletionHandler parent = this;
                    response.sendBody(rbuffer.duplicate().asReadOnlyBuffer(), null, new CompletionHandler<Integer, Void>() {

                        @Override
                        public void completed(Integer result, Void attachment) {
                            rbuffer.clear();
                            remote.read(rbuffer, attachment, parent);
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            parent.failed(exc, attachment);
                        }
                    });
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    response.getContext().offerBuffer(rbuffer);
                    response.finish(true);
                    try {
                        remote.close();
                    } catch (IOException ex) {
                    }
                }
            });

            final ByteBuffer qbuffer = request.getContext().pollBuffer();
            request.getChannel().read(qbuffer, null, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    if (result <= 0) {
                        failed(null, attachment); //获取信息完毕
                        return;
                    }
                    qbuffer.flip();
                    CompletionHandler parent = this;
                    remote.write(qbuffer, null, new CompletionHandler<Integer, Void>() {

                        @Override
                        public void completed(Integer result, Void attachment) {
                            qbuffer.clear();
                            request.getChannel().read(qbuffer, null, parent);
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            parent.failed(exc, attachment);
                        }
                    });
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    response.getContext().offerBuffer(qbuffer);
                    response.finish(true);
                    try {
                        remote.close();
                    } catch (IOException ex) {
                    }
                }
            });
        }

        @Override
        public void failed(Throwable exc, Void v) {
            response.finish(true);
            try {
                remote.close();
            } catch (IOException ex) {
            }
        }
    }

}
