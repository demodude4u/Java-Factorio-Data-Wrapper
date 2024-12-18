package com.demod.factorio;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import com.diffplug.common.base.Errors;
import com.diffplug.common.base.Throwing;
import com.google.common.collect.Streams;

public final class Utils {

	@SuppressWarnings("unchecked")
	public static <T> T convertLuaToJson(LuaValue value) {
		if (value.istable()) {
			if (isLuaArray(value)) {
				JSONArray json = new JSONArray();
				Utils.forEach(value, (v) -> {
					json.put(Utils.<Object>convertLuaToJson(v));
				});
				return (T) json;
			} else {
				JSONObject json = new JSONObject();
				terribleHackToHaveOrderedJSONObject(json);
				Utils.forEach(value, (k, v) -> {
					json.put(k.tojstring(), Utils.<Object>convertLuaToJson(v));
				});
				return (T) json;
			}
		} else {
			if (value.isnil()) {
				return null;
			} else if (value.isboolean()) {
				return (T) (Boolean) value.toboolean();
			} else if (value.isnumber()) {
				Double number = value.todouble();
				if (number == Double.POSITIVE_INFINITY) {
					return (T) "infinity";
				} else if (number == Double.NEGATIVE_INFINITY) {
					return (T) "-infinity";
				} else if (number == Double.NaN) {
					return (T) "NaN";
				} else {
					return (T) number;
				}
			}
			return (T) value.tojstring();
		}
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
				System.out.println(prefix + i + " = " + v);
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
				System.out.println(prefix + k + " = " + v);
			}
		});
	}

	public static void debugPrintLua(LuaValue value) {
		debugPrintLua("", value, System.out);
	}

	public static void debugPrintLua(LuaValue value, PrintStream ps) {
		debugPrintLua("", value, ps);
	}

	private static void debugPrintLua(String prefix, LuaValue value, PrintStream ps) {
		if (value.istable()) {
			forEachSorted(value, (k, v) -> {
				if (v.istable()) {
					debugPrintLua(prefix + k + ".", v, ps);
				} else {
					ps.println(prefix + k + " = " + v);
				}
			});
		} else {
			ps.println(prefix.isEmpty() ? value : (prefix + " = " + value));
		}
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
	public static <T> void forEach(JSONObject json, Throwing.BiConsumer<String, T> consumer) {
		json.keySet().stream().sorted()
				.forEach(Errors.rethrow().wrap((String k) -> consumer.accept(k, (T) json.get(k))));
	}

	public static void forEach(LuaValue table, BiConsumer<LuaValue, LuaValue> consumer) {
		LuaValue k = LuaValue.NIL;
		while (true) {
			Varargs n = table.next(k);
			if ((k = n.arg1()).isnil())
				break;
			LuaValue v = n.arg(2);
			consumer.accept(k, v);
		}
	}

	public static void forEach(LuaValue table, Consumer<LuaValue> consumer) {
		LuaValue k = LuaValue.NIL;
		while (true) {
			Varargs n = table.next(k);
			if ((k = n.arg1()).isnil())
				break;
			LuaValue v = n.arg(2);
			consumer.accept(v);
		}
	}

	public static void forEachSorted(LuaValue table, BiConsumer<LuaValue, LuaValue> consumer) {
		Streams.stream(new Iterator<Entry<LuaValue, LuaValue>>() {
			LuaValue k = LuaValue.NIL;
			Varargs next = null;

			@Override
			public boolean hasNext() {
				if (next == null) {
					next = table.next(k);
					k = next.arg1();
				}
				return !k.isnil();
			}

			@Override
			public Entry<LuaValue, LuaValue> next() {
				if (next == null) {
					next = table.next(k);
					k = next.arg1();
				}
				Entry<LuaValue, LuaValue> ret = new SimpleImmutableEntry<>(k, next.arg(2));
				next = null;
				return ret;
			}
		}).sorted((p1, p2) -> p1.getKey().toString().compareTo(p2.getKey().toString()))
				.forEach(p -> consumer.accept(p.getKey(), p.getValue()));
	}

	private static boolean isLuaArray(LuaValue value) {
		if (value.istable()) {
			LuaValue k = LuaValue.NIL;
			int i = 0;
			while (true) {
				i++;
				Varargs n = value.next(k);
				if ((k = n.arg1()).isnil())
					break;
				if (!k.isnumber() || k.toint() != i) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	public static Color parseColor(LuaValue value) {
		float red, green, blue, alpha;
		if (!value.get("r").isnil()) {
			red = value.get("r").tofloat();
			green = value.get("g").tofloat();
			blue = value.get("b").tofloat();
			alpha = !value.get("a").isnil() ? value.get("a").tofloat() : 1.0f;
		} else { // color defined as array/list
			red = value.get(1).tofloat();
			green = value.get(2).tofloat();
			blue = value.get(3).tofloat();
			alpha = value.length() == 4 ? value.get(4).tofloat() : 1.0f;
		}

		if (red > 1 || green > 1 || blue > 1 || alpha > 1) {
			red /= 255;
			green /= 255;
			blue /= 255;
			alpha /= 255;
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
		int w = image.getWidth();
		int h = image.getHeight();
		BufferedImage ret = new BufferedImage(w, h, image.getType());
		int[] pixels = new int[w * h];
		image.getRGB(0, 0, w, h, pixels, 0, w);
		for (int i = 0; i < pixels.length; i++) {
			int argb = pixels[i];

			int a = (((argb >> 24) & 0xFF) * tint.getAlpha()) / 255;
			int r = (((argb >> 16) & 0xFF) * tint.getRed()) / 255;
			int g = (((argb >> 8) & 0xFF) * tint.getGreen()) / 255;
			int b = (((argb) & 0xFF) * tint.getBlue()) / 255;

			pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
		}
		ret.setRGB(0, 0, w, h, pixels, 0, w);
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
