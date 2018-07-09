package com.peel.react;

import android.support.annotation.Nullable;
import android.util.SparseArray;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.koushikdutta.async.AsyncNetworkSocket;
import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.ListenCallback;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Created by aprock on 12/29/15.
 */
public final class TcpSocketManager {
    private SparseArray<Object> mClients = new SparseArray<Object>();

    private WeakReference<TcpSocketListener> mListener;
    private AsyncServer mServer = AsyncServer.getDefault();

    private int mInstances = 5000;

    public TcpSocketManager(TcpSocketListener listener) throws IOException {
        mListener = new WeakReference<TcpSocketListener>(listener);
    }

    private void setSocketCallbacks(final Integer cId, final AsyncSocket socket) {
        socket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                TcpSocketListener listener = mListener.get();
                if (listener != null) {
                    listener.onClose(cId, ex==null?null:ex.getMessage());
                }
            }
        });

        socket.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                TcpSocketListener listener = mListener.get();
                if (listener != null) {
                    listener.onData(cId, bb.getAllByteArray());
                }
            }
        });

        socket.setEndCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex != null) {
                    TcpSocketListener listener = mListener.get();
                    if (listener != null) {
                        listener.onError(cId, ex.getMessage());
                    }
                }
                socket.close();
            }
        });
    }

    public void listen(final Integer cId, final String host, final Integer port) throws UnknownHostException, IOException {
        // resolve the address
        final InetSocketAddress socketAddress;
        if (host != null) {
            socketAddress = new InetSocketAddress(InetAddress.getByName(host), port);
        } else {
            socketAddress = new InetSocketAddress(port);
        }

        mServer.listen(InetAddress.getByName(host), port, new ListenCallback() {
            @Override
            public void onListening(AsyncServerSocket socket) {
                mClients.put(cId, socket);

                TcpSocketListener listener = mListener.get();
                if (listener != null) {
                    listener.onConnect(cId, socketAddress);
                }
            }

            @Override
            public void onAccepted(AsyncSocket socket) {
                setSocketCallbacks(mInstances, socket);
                mClients.put(mInstances, socket);

                AsyncNetworkSocket socketConverted = Util.getWrappedSocket(socket, AsyncNetworkSocket.class);
                InetSocketAddress remoteAddress = socketConverted != null ? socketConverted.getRemoteAddress() : socketAddress;

                TcpSocketListener listener = mListener.get();
                if (listener != null) {
                    listener.onConnection(cId, mInstances, remoteAddress);
                }

                mInstances++;
            }

            @Override
            public void onCompleted(Exception ex) {
                mClients.delete(cId);

                TcpSocketListener listener = mListener.get();
                if (listener != null) {
                    listener.onClose(cId, ex != null ? ex.getMessage() : null);
                }
            }
        });
    }

    public void connect(final Integer cId, final @Nullable String host, final Integer port, final boolean useTls, final ReadableMap options) throws UnknownHostException, IOException {
        // resolve the address
        final InetSocketAddress socketAddress;
        if (host != null) {
            socketAddress = new InetSocketAddress(InetAddress.getByName(host), port);
        } else {
            socketAddress = new InetSocketAddress(port);
        }

        mServer.connectSocket(socketAddress, new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                if (useTls) {
                    Boolean rejectUnauthorized = true;
                    if(options.hasKey("rejectUnauthorized"))
                            rejectUnauthorized = options.getBoolean("rejectUnauthorized");
                    SSLContext sslContext = AsyncSSLSocketWrapper.getDefaultSSLContext();
                    TrustManager[] trustManagers = null;
                    HostnameVerifier hostnameVerifier = null;
                    if(!rejectUnauthorized)
                        try {
                            sslContext = SSLContext.getInstance("TLS");
                            trustManagers = new TrustManager[] {
                                    new X509TrustManager() {
                                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
                                    }
                            };
                            hostnameVerifier = new HostnameVerifier() {
                                @Override
                                public boolean verify(String s, SSLSession sslSession) {
                                    return true;
                                }
                            };
                            try {
                                sslContext.init(null, trustManagers, null);
                            } catch (KeyManagementException e) {
                                e.printStackTrace();
                            }
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }
                    AsyncSSLSocketWrapper.handshake(socket,
                            socketAddress.getHostName(),
                            socketAddress.getPort(),
                            sslContext.createSSLEngine(),
                            trustManagers,
                            hostnameVerifier,
                            true,
                            new AsyncSSLSocketWrapper.HandshakeCallback() {
                                @Override
                                public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket) {
                                    onConnectionCompleted(e, socket, cId, socketAddress);
                                }
                            });
                } else {
                    onConnectionCompleted(ex, socket, cId, socketAddress);
                }
            }
        });
    }

    private void onConnectionCompleted(Exception ex, AsyncSocket socket, Integer cId, InetSocketAddress socketAddress) {
        TcpSocketListener listener = mListener.get();
        mClients.put(cId, socket);
        if (ex == null) {
            setSocketCallbacks(cId, socket);

            if (listener != null) {
                listener.onConnect(cId, socketAddress);
            }
        } else if (listener != null) {
            listener.onError(cId, "unable to open socket: " + ex);
            close(cId);
        } else {
            close(cId);
        }
    }

    public void upgradeToSecure(final Integer cId, String host, Integer port, final Callback callback) {
        Object existingSocket = mClients.get(cId);
        if (existingSocket != null && existingSocket instanceof AsyncSocket) {
            AsyncSSLSocketWrapper.handshake((AsyncSocket) existingSocket,
                    host,
                    port,
                    AsyncSSLSocketWrapper.getDefaultSSLContext().createSSLEngine(),
                    null,
                    null,
                    true,
                    new AsyncSSLSocketWrapper.HandshakeCallback() {
                        @Override
                        public void onHandshakeCompleted(Exception ex, AsyncSSLSocket upgradedSocket) {
                            TcpSocketListener listener = mListener.get();
                            mClients.put(cId, upgradedSocket);
                            if (ex == null) {
                                setSocketCallbacks(cId, upgradedSocket);
                                if (listener != null) {
                                    listener.onSecureConnect(cId);
                                }
                                if (callback != null) {
                                    callback.invoke();
                                }
                            } else if (listener != null) {
                                listener.onError(cId, "unable to upgrade socket to tls: " + ex);
                                close(cId);
                            } else {
                                close(cId);
                            }
                        }
                    });
        }
    }

    public void write(final Integer cId, final byte[] data) {
        Object socket = mClients.get(cId);
        if (socket != null && socket instanceof AsyncSocket) {
            ((AsyncSocket) socket).write(new ByteBufferList(data));
        }
    }

    public void close(final Integer cId) {
        Object socket = mClients.get(cId);
        if (socket != null) {
            if (socket instanceof AsyncSocket) {
                ((AsyncSocket) socket).close();
            } else if (socket instanceof AsyncServerSocket) {
                ((AsyncServerSocket) socket).stop();
            }
        } else {
            TcpSocketListener listener = mListener.get();
            if (listener != null) {
               listener.onError(cId, "unable to find socket");
            }
        }
    }

    public void closeAllSockets() {
        for (int i = 0; i < mClients.size(); i++) {
            close(mClients.keyAt(i));
        }
        mClients.clear();
    }
}
