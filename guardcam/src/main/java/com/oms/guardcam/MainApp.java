package com.oms.guardcam;

import com.oms.guardcam.controller.MainController;
import com.oms.guardcam.view.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

public class MainApp extends Application {

    public static void main(String[] args) {
        // Tắt log đỏ của FFmpeg
        org.bytedeco.ffmpeg.global.avutil.av_log_set_level(org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR);
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        // 1. Khởi tạo Giao diện
        MainView mainView = new MainView();

        // 2. Khởi tạo Trình điều khiển (Gắn View vào Controller)
        MainController controller = new MainController(mainView);

        // 3. Hiển thị & Cấu hình Logo
        Scene scene = new Scene(mainView.getRoot(), 1366, 768);
        stage.setTitle("Order Guard Cam Pro - Enterprise Native");

        // --- ĐOẠN CODE THÊM LOGO CHO CỬA SỔ ---
        try {
            InputStream iconStream = getClass().getResourceAsStream("/logo.png");
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            } else {
                System.out.println("Không tìm thấy file logo.png trong thư mục resources!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // -------------------------------------

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            controller.shutdown();
            Platform.exit();
            System.exit(0);
        });
        stage.show();
    }
}