/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.mcp.sample.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

@Service
public class WeatherService {

	private static final String API_KEY = "去和风天气网站拿密钥"; // 替换为你的和风天气API密钥
	private static final String BASE_URL = "https://api.qweather.com/v7";
	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public WeatherService() {
		this.restClient = RestClient.builder()
			.baseUrl(BASE_URL)
			.defaultHeader("Accept", "application/json")
			.build();
		this.objectMapper = new ObjectMapper();
	}

	/**
	 * 获取城市ID
	 */
	private String getLocationId(String cityName) {
		try {
//			String encodedCity = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
			byte[] responseBytes = restClient.get()
				.uri("https://geoapi.qweather.com/v2/city/lookup?key={key}&location={city}", 
					API_KEY, cityName)
				.header("Accept-Charset", "UTF-8")
				.header("Content-Type", "application/json;charset=UTF-8")
				.retrieve()
				.toEntity(byte[].class)
				.getBody();

			byte[] decompressedData = WeatherService.decompress(responseBytes);
			String response = new String(decompressedData, "UTF-8");

			JsonNode root = objectMapper.readTree(response);
			if ("200".equals(root.path("code").asText())) {
				return root.path("location")
					.path(0)
					.path("id")
					.asText();
			}
		} catch (Exception e) {
			// 记录错误但继续执行
		}
		return null;
	}

	public static byte[] decompress(byte[] compressedData) throws IOException {
		ByteArrayInputStream bis = new ByteArrayInputStream(compressedData);
		GZIPInputStream gzipInputStream = new GZIPInputStream(bis);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len;
		while ((len = gzipInputStream.read(buffer))  > 0) {
			bos.write(buffer,  0, len);
		}
		gzipInputStream.close();
		bos.close();
		return bos.toByteArray();
	}

	/**
	 * 获取中国城市天气预报
	 */
	@Tool(name = "get_weather",
		  description = "获取中国城市的实时天气信息。输入城市名称，如：北京、上海、广州等")
	public String getWeatherForecastByLocation(
			@Parameter(name = "city",
					  description = "中国城市名称，如：北京、上海、广州等") 
			String city) {
		
		if (city == null || city.trim().isEmpty()) {
			return "请输入有效的城市名称";
		}

		try {
			String locationId = getLocationId(city.trim());
			if (locationId == null) {
				return String.format("找不到城市\"%s\"的天气信息，请确保输入正确的中国城市名称", city);
			}

			byte[] responseBytes = restClient.get()
				.uri("/weather/now?key={key}&location={id}", API_KEY, locationId)
				.header("Accept-Charset", "UTF-8")
				.header("Content-Type", "application/json;charset=UTF-8")
				.retrieve()
				.toEntity(byte[].class)
				.getBody();

			byte[] decompressedData = WeatherService.decompress(responseBytes);
			String response = new String(decompressedData, "UTF-8");

			JsonNode root = objectMapper.readTree(response);
			if ("200".equals(root.path("code").asText())) {
				JsonNode now = root.path("now");
				return String.format("""
					%s实时天气：
					• 天气：%s
					• 温度：%s°C
					• 体感温度：%s°C
					• 相对湿度：%s%%
					• %s %s级
					• 更新时间：%s
					""",
					city,
					now.path("text").asText(),
					now.path("temp").asText(),
					now.path("feelsLike").asText(),
					now.path("humidity").asText(),
					now.path("windDir").asText(),
					now.path("windScale").asText(),
					now.path("obsTime").asText()
				);
			}
			return "获取天气信息失败，请稍后重试";
		} catch (Exception e) {
			return String.format("获取天气信息时发生错误：%s", e.getMessage());
		}
	}

	/**
	 * 获取中国城市天气预警
	 */
	@Tool(name = "get_weather_warning",
		  description = "获取中国城市的天气预警信息。输入城市名称，如：北京、上海、广州等")
	public String getAlerts(
			@Parameter(name = "city", 
					  description = "中国城市名称，如：北京、上海、广州等") 
			String city) {
		
		if (city == null || city.trim().isEmpty()) {
			return "请输入有效的城市名称";
		}

		try {
			String locationId = getLocationId(city.trim());
			if (locationId == null) {
				return String.format("找不到城市\"%s\"的天气预警信息，请确保输入正确的中国城市名称", city);
			}

			byte[] responseBytes = restClient.get()
				.uri("/warning/now?key={key}&location={id}", API_KEY, locationId)
				.header("Accept-Charset", "UTF-8")
				.header("Content-Type", "application/json;charset=UTF-8")
				.retrieve()
				.toEntity(byte[].class)
				.getBody();

			byte[] decompressedData = WeatherService.decompress(responseBytes);
			String response = new String(decompressedData, "UTF-8");

			JsonNode root = objectMapper.readTree(response);
			if ("200".equals(root.path("code").asText())) {
				JsonNode warning = root.path("warning");
				if (warning.isArray() && warning.size() > 0) {
					StringBuilder warnings = new StringBuilder();
					warnings.append(String.format("%s天气预警信息：\n", city));
					for (JsonNode alert : warning) {
						warnings.append(String.format("""
							• 预警类型：%s
							• 预警级别：%s
							• 预警详情：%s
							• 发布时间：%s
							
							""",
							alert.path("typeName").asText(),
							alert.path("level").asText(),
							alert.path("text").asText(),
							alert.path("pubTime").asText()
						));
					}
					return warnings.toString();
				}
				return String.format("%s目前无天气预警信息", city);
			}
			return "获取天气预警信息失败，请稍后重试";
		} catch (Exception e) {
			return String.format("获取天气预警信息时发生错误：%s", e.getMessage());
		}
	}

	/**
	 * 用于本地测试的 main 方法
	 */
	public static void main(String[] args) {
		WeatherService weatherService = new WeatherService();
		
		// 测试获取天气信息
		System.out.println("=== 测试天气查询 ===");
		System.out.println(weatherService.getWeatherForecastByLocation("济南"));

		// 测试获取天气预警
		System.out.println("\n=== 测试天气预警 ===");
		System.out.println(weatherService.getAlerts("济南"));

		// 测试错误情况
		System.out.println("\n=== 测试错误处理 ===");
		System.out.println(weatherService.getWeatherForecastByLocation(""));  // 空输入
		System.out.println(weatherService.getWeatherForecastByLocation("111"));  // 无效城市名
	}
}