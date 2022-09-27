package com.springstudy.Initializethebean.Autowired;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/31 9:20 下午
 */
@Service
public class A1 {

	/**
	 * 有一个名称为a2的bean，其类型是A3
	 * 		@Autowired报错，显示不存在A2类型的bean
	 * 		@Resource报错，显示找到的bean类型为A3，于A2类型不符合
	 * 	（因此得以证明：@Autowired无法按照名称注入，只会按照类型注入！@Resource先按名称注入）
	 * 	采用@Resource，如果存在一个类型为A2的bean，但是名称不是a2，依然可以注入成功
	 * 	（因此得以证明：@Resource当名称无法注入时，会用类型注入）
	 * 	采用@Autowired+@Qualifier：存在一个A2类型的bean，但是名称不是a2，报错说找不到此bean，将@Qualifer去掉，则注入成功！
	 * 	（因此得以证明：@Autowired+@Qualifier只会采用名称注入！）
	 */
	@Autowired
	//@Qualifier
	//@Resource
	private A2 a2;	//没有接口也可以注入

	public A2 getA2() {
		return a2;
	}
}