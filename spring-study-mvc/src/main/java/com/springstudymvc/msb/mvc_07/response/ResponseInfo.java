package com.springstudymvc.msb.mvc_07.response;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 返回的信息封装
 * @date 2020/11/15 2:48 下午
 */
public class ResponseInfo<T> implements Serializable {

    static final long serialVersionUID = -7837778459320300947L;

    /**
     * 成功时的正常代码、默认提示信息。
     */
    public final static Integer SUCCESS = 1;

    public final static String SUCCESS_MSG = "成功";

    /**
     * 响应代码。
     */
    private Integer code;

    /**
     * 响应提示信息。
     */
    private String msg;

    /**
     * 响应数据。
     */
    private T data;

    /**
     * 当前请求的URL
     */
    private String requestUrl;

    private ResponseInfo(Integer code, String msg, T data, String requestUrl) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.requestUrl = requestUrl;
    }

    /* 成功，下面的区别是：返回不同的数据格式 */

    public static ResponseInfo<Object> success() {
        return new ResponseInfo<Object>(SUCCESS, SUCCESS_MSG, null, null);
    }

    public static ResponseInfo<Object> success(Object data) {
        return new ResponseInfo<Object>(SUCCESS, SUCCESS_MSG, data, null);
    }

    public static <T extends Serializable> ResponseInfo<T> success(T data) {
        return new ResponseInfo<T>(SUCCESS, SUCCESS_MSG, data, null);
    }

    public static <T extends Collection<? extends Serializable>> ResponseInfo<T> success(T data) {
        return new ResponseInfo<T>(SUCCESS, SUCCESS_MSG, data, null);
    }

    public static <T extends Map<? extends Serializable, ?>> ResponseInfo<T> success(T data) {
        return new ResponseInfo<T>(SUCCESS, SUCCESS_MSG, data, null);
    }

    public boolean isSuccess() {
        return SUCCESS.equals(this.code);
    }


	public static Integer getSUCCESS() {
		return SUCCESS;
	}

	public static String getSuccessMsg() {
		return SUCCESS_MSG;
	}

	public Integer getCode() {
		return code;
	}

	public void setCode(Integer code) {
		this.code = code;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public T getData() {
		return data;
	}

	public void setData(T data) {
		this.data = data;
	}

	public String getRequestUrl() {
		return requestUrl;
	}

	public void setRequestUrl(String requestUrl) {
		this.requestUrl = requestUrl;
	}

}