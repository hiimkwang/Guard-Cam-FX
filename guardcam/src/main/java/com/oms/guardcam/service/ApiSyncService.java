package com.oms.guardcam.service;

import com.oms.guardcam.model.OrderRecord;
import java.util.concurrent.CompletableFuture;

public class ApiSyncService {

    // Nơi bạn thực hiện call API sau này
    public CompletableFuture<Boolean> syncOrderData(OrderRecord order) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ví dụ: Đọc file ảnh frame cuối cùng hoặc trích xuất text
                // Gửi HTTP POST tới API tạo đơn hàng của ứng dụng quản lý
                System.out.println("Đang đồng bộ dữ liệu API cho đơn: " + order.getTrackingCode());

                // Giả lập network delay
                Thread.sleep(1500);

                order.setApiSynced(true);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }
}