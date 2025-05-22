package com.toyota.mainapp.subscriber.dynamic;

import com.toyota.mainapp.config.SubscriberDefinition;
import com.toyota.mainapp.coordinator.PlatformCallback;
import com.toyota.mainapp.subscriber.PlatformSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;

@Component
public class DynamicClassLoader {

    private static final Logger logger = LoggerFactory.getLogger(DynamicClassLoader.class);

    /**
     * Loads and instantiates a PlatformSubscriber based on its class name.
     * Initializes it with the provided definition and callback.
     *
     * @param className The fully qualified class name of the subscriber implementation.
     * @param definition The configuration for this subscriber.
     * @param callback The callback for the subscriber to use.
     * @return An initialized PlatformSubscriber instance, or null if loading fails.
     */
    public PlatformSubscriber loadSubscriber(String className, SubscriberDefinition definition, PlatformCallback callback) {
        try {
            logger.info("Abone sınıfı yüklenmeye çalışılıyor: {}", className);
            Class<?> subscriberClass = Class.forName(className);
            if (!PlatformSubscriber.class.isAssignableFrom(subscriberClass)) {
                logger.error("{} sınıfı PlatformSubscriber arayüzünü uygulamıyor.", className);
                return null;
            }

            Constructor<?> constructor = subscriberClass.getDeclaredConstructor();
            PlatformSubscriber subscriber = (PlatformSubscriber) constructor.newInstance();
            
            subscriber.initialize(definition, callback); // Initialize after instantiation
            
            logger.info("Abone başarıyla yüklendi ve başlatıldı: {}, platform: {}", className, definition.getName());
            return subscriber;
        } catch (ClassNotFoundException e) {
            logger.error("Abone sınıfı bulunamadı: {}", className, e);
        } catch (NoSuchMethodException e) {
            logger.error("Abone sınıfı için varsayılan yapıcı bulunamadı: {}", className, e);
        } catch (Exception e) {
            logger.error("Abone sınıfı {} örneği oluşturulamadı veya başlatılamadı: {}", className, e.getMessage(), e);
        }
        return null;
    }
}
