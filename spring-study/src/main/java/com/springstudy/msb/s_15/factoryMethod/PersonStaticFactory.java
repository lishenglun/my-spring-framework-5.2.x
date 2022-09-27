package com.springstudy.msb.s_15.factoryMethod;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 静态工厂
 * @date 2022/5/6 2:21 下午
 */
public class PersonStaticFactory {

	public static Person getPerson(String name) {
		Person person = new Person();
		person.setId(1);
		person.setName(name);
		return person;
	}

}