package com.lt.push.config;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author litong
 * @date 2019/11/1 13:32
 */
@Component
@Slf4j
public class MessagePushConfig implements InitializingBean {
    @Resource
    private EventListenner eventListenner;

    @Value("${push.server.port}")
    private int serverPort;

    @Autowired
    private SocketIOServer socketIOServer;

    @Override
    public void afterPropertiesSet() throws Exception {
        socketIOServer.start();
        System.out.println("启动正常");
    }

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setPort(serverPort);

        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        socketConfig.setTcpNoDelay(true);
        socketConfig.setSoLinger(0);
        config.setSocketConfig(socketConfig);
        config.setHostname("localhost");

        SocketIOServer server = new SocketIOServer(config);
        server.addListeners(eventListenner);
        return server;
    }
}
