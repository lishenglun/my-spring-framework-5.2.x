package com.springstudy.dao;

import com.springstudy.anno.Select;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/8/23 12:48 上午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public interface SystemDao {

	@Select("select * from SystemDao where name = #{name}")
	public void getUserInfo(String name);


	@Select(value = "select * from SystemDao")
	public void getUserInfoList();

}