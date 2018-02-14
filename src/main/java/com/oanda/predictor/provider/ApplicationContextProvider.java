package com.oanda.predictor.provider;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Провайдер предоставляет доступ к Spring ApplicationContext
 */
@Component
public class ApplicationContextProvider {

    @Getter
    private static ApplicationContext applicationContext;

    @Autowired
    public ApplicationContextProvider(ApplicationContext applicationContext) {
        ApplicationContextProvider.applicationContext = applicationContext;
    }
}
