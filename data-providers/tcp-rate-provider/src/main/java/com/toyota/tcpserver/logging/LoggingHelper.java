package com.toyota.tcpserver.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * LoggingHelper - Standart formatta loglar üreten yardımcı sınıf.
 */
public class LoggingHelper {
    
    // İşlem tipleri için sabitler
    public static final String OPERATION_SUBSCRIBE = "SUBSCRIBE";
    public static final String OPERATION_UNSUBSCRIBE = "UNSUBSCRIBE";
    public static final String OPERATION_UPDATE = "UPDATE";
    public static final String OPERATION_CONNECT = "CONNECT";
    public static final String OPERATION_DISCONNECT = "DISCONNECT";
    public static final String OPERATION_ERROR = "ERROR";
    public static final String OPERATION_ALERT = "ALERT";
    public static final String OPERATION_INFO = "INFO";
    public static final String OPERATION_START = "START";
    public static final String OPERATION_STOP = "STOP";
    
    // Platform sabitleri
    public static final String PLATFORM_PF1 = "PF1";
    public static final String PLATFORM_PF2 = "PF2";
    public static final String PLATFORM_TCP = "TCP";
    public static final String PLATFORM_REST = "REST";
    
    private final Logger logger;
    
    /**
     * Belirtilen sınıf için yeni bir LoggingHelper örneği oluşturur.
     * 
     * @param clazz Log kaynağı olarak kullanılacak sınıf
     */
    public LoggingHelper(Class<?> clazz) {
        this.logger = LogManager.getLogger(clazz);
    }
    
    /**
     * Formatlanmış INFO log mesajı oluşturur.
     * 
     * @param operationType İşlem tipi (LoggingHelper.OPERATION_* sabitleri)
     * @param platform Platform (LoggingHelper.PLATFORM_* sabitleri)
     * @param pairName Döviz çifti adı (opsiyonel, null olabilir)
     * @param message Log mesajı
     */
    public void info(String operationType, String platform, String pairName, String message) {
        String formattedMessage = formatMessage(operationType, platform, pairName, null, message);
        logger.info(formattedMessage);
    }
    
    /**
     * Kur bilgisiyle birlikte formatlanmış INFO log mesajı oluşturur.
     * 
     * @param operationType İşlem tipi (LoggingHelper.OPERATION_* sabitleri)
     * @param platform Platform (LoggingHelper.PLATFORM_* sabitleri)
     * @param pairName Döviz çifti adı
     * @param rateInfo Kur bilgisi (örn: "BID:33.45 ASK:33.55")
     * @param message Log mesajı
     */
    public void info(String operationType, String platform, String pairName, String rateInfo, String message) {
        String formattedMessage = formatMessage(operationType, platform, pairName, rateInfo, message);
        logger.info(formattedMessage);
    }
    
    /**
     * Formatlanmış DEBUG log mesajı oluşturur.
     */
    public void debug(String operationType, String platform, String pairName, String message) {
        String formattedMessage = formatMessage(operationType, platform, pairName, null, message);
        logger.debug(formattedMessage);
    }
    
    /**
     * Kur bilgisiyle birlikte formatlanmış DEBUG log mesajı oluşturur.
     */
    public void debug(String operationType, String platform, String pairName, String rateInfo, String message) {
        String formattedMessage = formatMessage(operationType, platform, pairName, rateInfo, message);
        logger.debug(formattedMessage);
    }
    
    /**
     * Formatlanmış WARN log mesajı oluşturur.
     */
    public void warn(String operationType, String platform, String pairName, String message) {
        String formattedMessage = formatMessage(operationType, platform, pairName, null, message);
        logger.warn(formattedMessage);
    }
    
    /**
     * ALERT seviyesi için özel WARN log mesajı oluşturur (Log4j2'de doğrudan ALERT seviyesi olmadığından).
     */
    public void alert(String platform, String pairName, String message) {
        String formattedMessage = formatMessage(OPERATION_ALERT, platform, pairName, null, message);
        logger.warn(formattedMessage);
    }
    
    /**
     * Formatlanmış ERROR log mesajı oluşturur.
     */
    public void error(String platform, String pairName, String message, Throwable throwable) {
        String formattedMessage = formatMessage(OPERATION_ERROR, platform, pairName, null, message);
        logger.error(formattedMessage, throwable);
    }
    
    /**
     * Throwable olmadan formatlanmış ERROR log mesajı oluşturur.
     */
    public void error(String platform, String pairName, String message) {
        String formattedMessage = formatMessage(OPERATION_ERROR, platform, pairName, null, message);
        logger.error(formattedMessage);
    }
    
    /**
     * Formatlanmış FATAL log mesajı oluşturur.
     */
    public void fatal(String platform, String pairName, String message, Throwable throwable) {
        String formattedMessage = formatMessage(OPERATION_ERROR, platform, pairName, null, message);
        logger.fatal(formattedMessage, throwable);
    }
    
    /**
     * TRACE seviyesinde log mesajı oluşturur.
     */
    public void trace(String operationType, String platform, String pairName, String rateInfo, String message) {
        String formattedMessage = formatMessage(operationType, platform, pairName, rateInfo, message);
        logger.trace(formattedMessage);
    }
    
    /**
     * Log mesajını [IŞLEM_TIPI] [PLATFORM] [DÖVİZ_ÇİFTİ] [KUR_BİLGİSİ] - Mesaj içeriği formatına dönüştürür.
     */
    private String formatMessage(String operationType, String platform, String pairName, String rateInfo, String message) {
        StringBuilder builder = new StringBuilder();
        
        builder.append("[").append(operationType).append("] ");
        builder.append("[").append(platform).append("] ");
        
        if (pairName != null && !pairName.isEmpty()) {
            builder.append("[").append(pairName).append("] ");
        }
        
        if (rateInfo != null && !rateInfo.isEmpty()) {
            builder.append("[").append(rateInfo).append("] ");
        }
        
        builder.append("- ").append(message);
        
        return builder.toString();
    }
}
