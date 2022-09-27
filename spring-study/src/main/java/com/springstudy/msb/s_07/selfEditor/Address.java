package com.springstudy.msb.s_07.selfEditor;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/15 11:09 下午
 */
public class Address {

	// 省
	private String province;
	// 市
	private String city;
	// 区
	private String area;

	public String getProvince() {
		return province;
	}

	public void setProvince(String province) {
		this.province = province;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getArea() {
		return area;
	}

	public void setArea(String area) {
		this.area = area;
	}

	@Override
	public String toString() {
		return "Address{" +
				"province='" + province + '\'' +
				", city='" + city + '\'' +
				", area='" + area + '\'' +
				'}';
	}
}