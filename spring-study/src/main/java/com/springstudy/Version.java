package com.springstudy;

import org.springframework.core.SpringVersion;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2022/9/10 5:29 下午
 */
public class Version {

	/**
	 * 查看当前spring的版本
	 *
	 * 注意：⚠️spring的版本在gradle.properties文件中定义着
	 */
	public static void main(String[] args) {
		String version = SpringVersion.getVersion();

		System.out.println("===============");
		// 5.2.9.BUILD-SNAPSHOT
		System.out.println(version);
		System.out.println("===============");

		// 查看spring boot的版本
		//String version2 = SpringBootVersion.getVersion();
	}

}