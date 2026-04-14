package com.oms.guardcam.model;

import java.util.Date;

public class OrderRecord {
    private String trackingCode;
    private Date scanTime;
    private String panoVideoPath;
    private String qrVideoPath;
    private String mergedVideoPath;
    private boolean isApiSynced;

    public OrderRecord(String trackingCode) {
        this.trackingCode = trackingCode;
        this.scanTime = new Date();
        this.isApiSynced = false;
    }

    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }

    public Date getScanTime() { return scanTime; }

    public String getPanoVideoPath() { return panoVideoPath; }
    public void setPanoVideoPath(String panoVideoPath) { this.panoVideoPath = panoVideoPath; }

    public String getQrVideoPath() { return qrVideoPath; }
    public void setQrVideoPath(String qrVideoPath) { this.qrVideoPath = qrVideoPath; }

    public String getMergedVideoPath() { return mergedVideoPath; }
    public void setMergedVideoPath(String mergedVideoPath) { this.mergedVideoPath = mergedVideoPath; }

    public boolean isApiSynced() { return isApiSynced; }
    public void setApiSynced(boolean apiSynced) { isApiSynced = apiSynced; }
}