package com.hazelcast.remotecontroller;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HzDockerCluster {

    private String id = UUID.randomUUID().toString();
    private final String dockerImageString;
    private final String xmlConfigPath;
    private final ConcurrentHashMap<String, GenericContainer> containers = new ConcurrentHashMap<>();

    public HzDockerCluster(String dockerImageString, String xmlConfigPath) throws IOException {
        this.dockerImageString = dockerImageString;
        this.xmlConfigPath = xmlConfigPath;
    }

    public String getXmlConfigPath() {
        return xmlConfigPath;
    }

    public String getDockerImageString() {
        return dockerImageString;
    }

    public String getId() {
        return id;
    }

    public boolean addInstance(String containerId, GenericContainer container) {
        return this.containers.putIfAbsent(containerId, container) == null;
    }

    public GenericContainer getContainerById(String id) {
        return this.containers.get(id);
    }

    public void removeContainer(String containerId) {
        this.containers.remove(containerId);
    }

    public Collection<GenericContainer> getContainers() {
        return containers.values();
    }

    public void shutdown() {
        Iterator<GenericContainer> iterator = this.containers.values().iterator();
        if (iterator.hasNext()) {
            iterator.next().close();
        }
        this.containers.clear();
    }
}
