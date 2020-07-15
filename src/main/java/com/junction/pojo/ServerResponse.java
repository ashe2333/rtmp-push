package com.junction.pojo;

public class ServerResponse<T> {

    public int code;
    public String message;
    public T data;
    public boolean success;
}
