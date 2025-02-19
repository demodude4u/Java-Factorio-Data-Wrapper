package com.demod.factorio;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Scanner;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demod.factorio.fakelua.LuaTable;
import com.demod.factorio.fakelua.LuaValue;
import com.google.common.collect.Streams;

public final class Utils {
	@FunctionalInterface
	public static interface ThrowingBiConsumer<T, U> {
		void accept(T t, U u) throws Throwable;
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

	@SuppressWarnings("unchecked")
	public static <T> T convertLuaToJson(LuaValue value) {
		return (T) value.getJson();
	}

	public static void debugPrintJson(JSONArray json) {
		debugPrintJson("", json);
	}

	public static void debugPrintJson(JSONObject json) {
		debugPrintJson("", json);
	}

	private static void debugPrintJson(String prefix, JSONArray json) {
		forEach(json, (i, v) -> {
			if (v instanceof JSONArray) {
				debugPrintJson(prefix + "[" + i + "]", (JSONArray) v);
			} else if (v instanceof JSONObject) {
				debugPrintJson(prefix + "[" + i + "].", (JSONObject) v);
			} else {
				LOGGER.debug("{}{} = {}", prefix, i, v);
			}
		});
	}

	private static void debugPrintJson(String prefix, JSONObject json) {
		forEach(json, (k, v) -> {
			if (v instanceof JSONArray) {
				debugPrintJson(prefix + k, (JSONArray) v);
			} else if (v instanceof JSONObject) {
				debugPrintJson(prefix + k + ".", (JSONObject) v);
			} else {
				LOGGER.debug("{}{} = {}", prefix, k, v);
			}
		});
	}

	public static void debugPrintLua(LuaValue value) {
		LOGGER.debug(((JSONObject) value.getJson()).toString(2));
	}

	@SuppressWarnings("unchecked")
	public static <T> void forEach(JSONArray jsonArray, BiConsumer<Integer, T> consumer) {
		for (int i = 0; i < jsonArray.length(); i++) {
			consumer.accept(i, (T) jsonArray.get(i));
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> void forEach(JSONArray jsonArray, Consumer<T> consumer) {
		for (int i = 0; i < jsonArray.length(); i++) {
			consumer.accept((T) jsonArray.get(i));
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> void forEach(JSONObject json, ThrowingBiConsumer<String, T> consumer) {
		json.keySet().stream().sorted().forEach((String k) -> {
			try {
				consumer.accept(k, (T) json.get(k));
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		});
	}

	public static void forEach(LuaTable table, BiConsumer<LuaValue, LuaValue> consumer) {
		Object json = table.getJson();
		if (json instanceof JSONObject) {
			forEach((JSONObject) json, (k, v) -> consumer.accept(new LuaValue(k), new LuaValue(v)));
		} else {
			forEach((JSONArray) json, (i, v) -> consumer.accept(new LuaValue(i + 1), new LuaValue(v)));
		}

	}

	public static void forEach(LuaTable table, Consumer<LuaValue> consumer) {
		Object json = table.getJson();
		if (json instanceof JSONObject) {
			forEach((JSONObject) json, (k, v) -> consumer.accept(new LuaValue(v)));
		} else {
			forEach((JSONArray) json, (v) -> consumer.accept(new LuaValue(v)));
		}
	}

	public static void forEachSorted(LuaValue table, BiConsumer<LuaValue, LuaValue> consumer) {
		Object json = table.getJson();
		if (json instanceof JSONObject) {
			Streams.stream(((JSONObject) json).keys()).sorted()
					.forEach(k -> consumer.accept(new LuaValue(k), new LuaValue(((JSONObject) json).get(k))));
		} else if (json instanceof JSONArray) {
			forEach((JSONArray) json, (i, v) -> consumer.accept(new LuaValue(i + 1), new LuaValue(v)));
		}
	}

	public static Color parseColor(LuaValue value) {
		float red, green, blue, alpha = 0;
		boolean alphaPresent = false;
		if (value.checktable().isObject()) {
			red = value.get("r").tofloat();
			green = value.get("g").tofloat();
			blue = value.get("b").tofloat();
			alphaPresent = !value.get("a").isnil();
			if (alphaPresent) {
				alpha = value.get("a").tofloat();
			}
		} else { // color defined as array/list
			red = value.get(1).tofloat();
			green = value.get(2).tofloat();
			blue = value.get(3).tofloat();
			alphaPresent = value.length() == 4;
			if (alphaPresent) {
				alpha = value.get(4).tofloat();
			}
		}

		if (red > 1 || green > 1 || blue > 1 || (alphaPresent && alpha > 1)) {
			red /= 255;
			green /= 255;
			blue /= 255;
			alpha /= 255;
		}

		if (!alphaPresent) {
			alpha = 1.0f;
		}

		return new Color(red, green, blue, alpha);
	}

	public static Point parsePoint(LuaValue value) {
		if (value.isnil()) {
			return new Point();
		}
		return new Point(value.get(1).checkint(), value.get(2).checkint());
	}

	public static Point2D.Double parsePoint2D(JSONObject json) {
		return new Point2D.Double(json.getDouble("x"), json.getDouble("y"));
	}

	public static Point2D.Double parsePoint2D(LuaValue value) {
		if (value.isnil()) {
			return new Point2D.Double();
		}
		return new Point2D.Double(value.get(1).checkdouble(), value.get(2).checkdouble());
	}

	public static Rectangle parseRectangle(JSONArray json) {
		return new Rectangle(json.getInt(0), json.getInt(1), json.getInt(2), json.getInt(3));
	}

	public static Rectangle2D.Double parseRectangle(LuaValue value) {
		LuaTable table = value.checktable();
		LuaValue p1 = table.get(1);
		LuaValue p2 = table.get(2);
		double x1 = p1.get(1).checkdouble();
		double y1 = p1.get(2).checkdouble();
		double x2 = p2.get(1).checkdouble();
		double y2 = p2.get(2).checkdouble();
		return new Rectangle2D.Double(x1, y1, x2 - x1, y2 - y1);
	}

	public static Rectangle2D.Double parseRectangle2D(JSONArray json) {
		return new Rectangle2D.Double(json.getDouble(0), json.getDouble(1), json.getDouble(2), json.getDouble(3));
	}

	@SuppressWarnings("resource")
	public static JSONObject readJsonFromStream(InputStream in) throws JSONException {
		return new JSONObject(new Scanner(in, "UTF-8").useDelimiter("\\A").next());
	}

	public static void terribleHackToHaveOrderedJSONObject(JSONObject json) {
		try {
			Field map = json.getClass().getDeclaredField("map");
			map.setAccessible(true);// because the field is private final...
			map.set(json, new LinkedHashMap<>());
			map.setAccessible(false);// return flag
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			System.exit(0); // Oh well...
		}
	}

	public static BufferedImage tintImage(BufferedImage image, Color tint) {
		if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
			BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(),
					BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = converted.createGraphics();
			g.drawImage(image, 0, 0, null);
			g.dispose();
			image = converted;
		}

		int w = image.getWidth();
		int h = image.getHeight();
		BufferedImage ret = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

		float scaleR = tint.getRed() / 255f;
		float scaleG = tint.getGreen() / 255f;
		float scaleB = tint.getBlue() / 255f;
		float scaleA = tint.getAlpha() / 255f;
		float[] scales = { scaleR, scaleG, scaleB, scaleA };
		float[] offsets = { 0f, 0f, 0f, 0f };
		RescaleOp rescaleOp = new RescaleOp(scales, offsets, null);
		rescaleOp.filter(image, ret);
		return ret;
	}

	public static JSONArray toJson(Rectangle rectangle) {
		JSONArray json = new JSONArray();
		json.put(rectangle.x);
		json.put(rectangle.y);
		json.put(rectangle.width);
		json.put(rectangle.height);
		return json;
	}

	public static JSONArray toJson(Rectangle2D.Double rectangle) {
		JSONArray json = new JSONArray();
		json.put(rectangle.x);
		json.put(rectangle.y);
		json.put(rectangle.width);
		json.put(rectangle.height);
		return json;
	}

	private Utils() {
	}
}
