package com.oms.guardcam;

import com.oms.guardcam.controller.MainController;
import com.oms.guardcam.view.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

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

        // 3. Hiển thị
        Scene scene = new Scene(mainView.getRoot(), 1366, 768);
        stage.setTitle("Order Guard Cam Pro - Enterprise Native");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            controller.shutdown();
            Platform.exit();
            System.exit(0);
        });
        stage.show();
    }
}