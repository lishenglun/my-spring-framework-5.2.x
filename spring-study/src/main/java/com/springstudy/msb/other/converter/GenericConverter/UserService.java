package com.springstudy.msb.other.converter.GenericConverter;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/10/7 11:05
 */
public interface UserService {

	User findById(Integer source);

	User find(String source);

}