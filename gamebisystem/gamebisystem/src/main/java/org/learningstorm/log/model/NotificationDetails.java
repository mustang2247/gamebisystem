package org.learningstorm.log.model;

/**
 * 提示的详细信息
 */
public class NotificationDetails {
    public NotificationDetails(String to, Severity severity, String message) {
        this.to = to;
        this.severity = severity;
        this.message = message;
    }

    public String getTo() {
        return to;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    //目标
    private String to;
    //重要等级
    private Severity severity;
    //消息
    private String message;
}
