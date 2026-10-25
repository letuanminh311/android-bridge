package com.example.bridge_lib;

/** C# cài đặt interface này (qua AndroidJavaProxy) để nhận thông báo có data mới. */
public interface UpdateListener {
    void notifyUpdateShareMemory();
}