//package com.springstudymvc.msb.mvc_06;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
///**
// * @author lishenglun
// * @version v1.0.0
// * @description 转换器 —— 所有的转换器都在这里添加
// * @date 2020/11/19 5:41 下午
// */
//@Configuration
//public class ConvertAdditionConfig implements WebMvcConfigurer {
//
//    /**
//     * @return MappingJackson2HttpMessageConverter
//     * MappingJackson2HttpMessageConverter 实现了HttpMessageConverter接口；
//     * httpMessageConverters.getConverters()返回的对象里包含了MappingJackson2HttpMessageConverter
//     * @description 把后段的数据转换成某一类型，返回给到前端
//     * @author lishenglun
//     * @date 2021/5/8 6:06 下午
//     */
//    @Bean
//    public MappingJackson2XmlHttpMessageConverter getMappingJackson2HttpMessageConverter() {
//        return new MappingJackson2XmlHttpMessageConverter();
//    }
//
//}