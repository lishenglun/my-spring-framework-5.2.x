package com.springstudymvc.msb.mvc_07.response;

import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description controller返回值统一包装
 * @date 2020/11/20 2:05 下午
 */
@RestControllerAdvice(basePackages = "com.loanofficer.cloud.controller") /* 声明该类要处理的包路径 */
@Order(Integer.MIN_VALUE)
public class ResponseInfoControllerAdvice implements ResponseBodyAdvice<Object> {

    /**
     * @param returnType
     * @param aClass
     * @return
     * @description 判断是否要执行beforeBodyWrite方法，true为执行，false不执行
     * @author lishenglun
     * @date 2021/3/4 9:52 上午
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> aClass) {
        // 如果接口返回的类型本身就是Resp那就没有必要进行额外的操作，返回false
        return !returnType.getGenericParameterType().equals(ResponseInfo.class);
    }

    /**
     * @param data       controller返回的主体
     * @param returnType
     * @param mediaType
     * @param aClass
     * @param request
     * @param response
     * @return
     * @description 对response处理的执行方法
     * @author lishenglun
     * @date 2021/3/4 9:52 上午
     */
    @Override
    public Object beforeBodyWrite(Object data, MethodParameter returnType, MediaType mediaType,
								  Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest request, ServerHttpResponse response) {
        // 可以指定某些url不需要包装
        //String url = request.getURI().getPath();
        //if (list.contains(url)) {
        //    return data;
        //}
        // String类型不能直接包装，所以要进行些特别的处理
        // 因为StringHttpMessageConverter会直接把字符串写入body, 所以字符串特殊处理
        if (returnType.getGenericParameterType().equals(String.class)) {

            //return JSON.toJSONString(ResponseInfo.success(data));
        }
        // 将原本的数据包装在Resp里
        return ResponseInfo.success(data);
    }

}