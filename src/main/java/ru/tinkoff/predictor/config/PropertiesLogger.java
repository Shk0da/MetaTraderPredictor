package ru.tinkoff.predictor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import ru.tinkoff.predictor.actor.LearnActor;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Configuration
public class PropertiesLogger {

    public static Logger log = LoggerFactory.getLogger(LearnActor.class);

    @Autowired
    private AbstractEnvironment environment;

    @PostConstruct
    public void printProperties() {
        log.info("**** APPLICATION PROPERTIES SOURCES ****");

        Set<String> properties = new TreeSet<>();
        for (PropertiesPropertySource p : findPropertiesPropertySources()) {
            log.info(p.toString());
            properties.addAll(Arrays.asList(p.getPropertyNames()));
        }

        log.info("**** APPLICATION PROPERTIES VALUES ****");
        print(properties);

    }

    private List<PropertiesPropertySource> findPropertiesPropertySources() {
        List<PropertiesPropertySource> propertiesPropertySources = new LinkedList<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof PropertiesPropertySource) {
                propertiesPropertySources.add((PropertiesPropertySource) propertySource);
            }
        }

        return propertiesPropertySources;
    }

    private void print(Set<String> properties) {
        for (String propertyName : properties) {
            log.info("{}={}", propertyName, environment.getProperty(propertyName));
        }
    }
}
