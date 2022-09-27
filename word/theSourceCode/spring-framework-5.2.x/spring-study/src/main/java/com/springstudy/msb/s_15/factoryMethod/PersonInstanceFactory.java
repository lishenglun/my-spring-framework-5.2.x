package com.springstudy.msb.s_15.factoryMethod;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 实例工厂
 * @date 2022/5/6 2:20 下午
 */
public class PersonInstanceFactory {

	public Person getPerson(String name) {
		Person person = new Person();
		person.setId(1);
		person.setName(name);
		return person;
	}

}