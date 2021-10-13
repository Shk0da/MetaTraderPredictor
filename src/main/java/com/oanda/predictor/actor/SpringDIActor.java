package com.oanda.predictor.actor;

import akka.actor.Actor;
import akka.actor.IndirectActorProducer;
import com.oanda.predictor.provider.ApplicationContextProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpringDIActor implements IndirectActorProducer {

    private Actor actorInstance;
    private final Class<? extends Actor> type;
    private final String instrument;
    private final Integer step;

    public SpringDIActor(Class<? extends Actor> type, String instrument, Integer step) {
        this.type = type;
        this.instrument = instrument;
        this.step = step;
    }

    @Override
    public Class<? extends Actor> actorClass() {
        return type;
    }

    @Override
    public Actor produce() {
        Actor newActor = actorInstance;
        actorInstance = null;
        if (newActor == null) {
            try {
                newActor = type.getConstructor(String.class, Integer.class).newInstance(instrument, step);
            } catch (Exception e) {
                log.error("Unable to create actor of type: {}. {}. {}", type, instrument, step, e);
            }
        }

        ApplicationContextProvider.getApplicationContext().getAutowireCapableBeanFactory().autowireBean(newActor);
        return newActor;
    }
}