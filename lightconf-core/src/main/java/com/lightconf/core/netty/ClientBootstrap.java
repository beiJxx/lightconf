package com.lightconf.core.netty;

import com.lightconf.common.model.Constants;
import com.lightconf.common.model.LoginMsg;
import com.lightconf.common.util.Base64Utils;
import com.lightconf.common.util.CommonConstants;
import com.lightconf.common.util.RSAUtils;
import com.lightconf.core.core.LightConfPropConf;
import com.lightconf.core.env.Environment;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ClientBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(ClientBootstrap.class);

    private int port;
    private String host;
    private int retryDelay;
    private SocketChannel socketChannel;
    private Bootstrap bootstrap;
    private NioEventLoopGroup workGroup = new NioEventLoopGroup(4);

    static {
        //使用 connector name 作为客户端唯一标识
        Constants.setClientId(LightConfPropConf.get(Environment.APPLICATION_UUID));
    }

    public ClientBootstrap(String host,int port) throws InterruptedException {
        this.port = port;
        this.host = host;
        this.retryDelay = CommonConstants.RETRY_DELAY;
        start();
    }

    public void start() {

        bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .group(workGroup)
                .remoteAddress(host, port)
                .handler(new ClientInitializer());
        doConnect(port, host);
    }

    /**
     * 建立连接，并且可以实现自动重连.
     * @param port port.
     * @param host host.
     * @throws InterruptedException InterruptedException.
     */
    public void doConnect(int port, String host) {

        if (socketChannel != null && socketChannel.isActive()) {
            return;
        }

        final int portConnect = port;
        final String hostConnect = host;

        ChannelFuture future = bootstrap.connect(host, port);

        //连接断开之后,重试连接
        future.addListener(new ChannelFutureListener() {

            int reConnect = 0;

            @Override
            public void operationComplete(ChannelFuture futureListener) throws Exception {

                if (futureListener.isSuccess()) {
                    socketChannel = (SocketChannel) futureListener.channel();
                    logger.info("Connect to server successfully! [host:" + hostConnect + ", port:" + portConnect + ", connector name:" + Constants.getClientId() + "]");
                    login(socketChannel);
                } else if (reConnect < CommonConstants.RECONNECT) {
                    reConnect++;
                    logger.info("Failed to connect to server, try connect after " + retryDelay + "s [host:" + hostConnect + ", port:" + portConnect + ", connector name:" + Constants.getClientId() + "]");
                    futureListener.channel().eventLoop().schedule(new Runnable() {
                        @Override
                        public void run() {
                            doConnect(portConnect,hostConnect);
                        }
                    }, retryDelay, TimeUnit.SECONDS);
                }
            }
        });
    }

    public static void login(SocketChannel socketChannel) {
        try {
            String base64 = Base64Utils.encode(Constants.getClientId().getBytes());
            byte[] encryDataByte = RSAUtils.encryptByPublicKey(Base64Utils.decode(base64), "");
            String encryData = Base64Utils.encode(encryDataByte);
            LoginMsg loginMsg = new LoginMsg();
//            loginMsg.setEncryData(encryData);
//            loginMsg.setPublicKey(publicKey);
            socketChannel.writeAndFlush(loginMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
