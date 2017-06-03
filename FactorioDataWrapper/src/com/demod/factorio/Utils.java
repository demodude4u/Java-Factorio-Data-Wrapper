package com.demod.factorio;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Scanner;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class Utils {
	public static final BufferedImage EMPTY_IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
	static {
		EMPTY_IMAGE.setRGB(0, 0, 0x00000000);
	}

	public static int compareRange(double min1, double max1, double min2, double max2) {
		if (max1 <= min2) {
			return -1;
		}
		if (max2 <= min1) {
			return 1;
		}
		return 0;
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
		debugPrintLua("", value);
	}

	private static void debugPrintLua(String prefix, LuaValue value) {
		if (value.istable()) {
			forEach(value, (k, v) -> {
				if (v.istable()) {
					debugPrintLua(prefix + k + ".", v);
				} else {
					System.out.println(prefix + k + " = " + v);
				}
			});
		} else {
			System.out.println(prefix.isEmpty() ? value : (prefix + " = " + value));
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
	public static <T> void forEach(JSONObject json, BiConsumer<String, T> consumer) {
		json.keySet().stream().sorted().forEach(k -> consumer.accept(k, (T) json.get(k)));
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

	public static Color getAverageColor(BufferedImage image) {
		int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
		float sumR = 0, sumG = 0, sumB = 0, sumA = 0;
		for (int pixel : pixels) {
			float a = (pixel >> 24) & 0xFF;
			float f = a / 255;
			sumA += a;
			sumR += ((pixel >> 16) & 0xFF) * f;
			sumG += ((pixel >> 8) & 0xFF) * f;
			sumB += ((pixel) & 0xFF) * f;
		}
		return new Color(sumR / sumA, sumG / sumA, sumB / sumA);
	}

	public static Color parseColor(LuaValue value) {
		float red = value.get("r").tofloat();
		float green = value.get("g").tofloat();
		float blue = value.get("b").tofloat();
		float alpha = value.get("a").tofloat();
		return new Color(red, green, blue, alpha);
	}

	public static Point parsePoint(LuaValue value) {
		if (value.isnil()) {
			return new Point();
		}
		return new Point(value.get(1).checkint(), value.get(2).checkint());
	}

	public static Double parsePoint2D(LuaValue value) {
		if (value.isnil()) {
			return new Point2D.Double();
		}
		return new Point2D.Double(value.get(1).checkdouble(), value.get(2).checkdouble());
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

	@SuppressWarnings("resource")
	public static JSONObject readJsonFromStream(InputStream in) throws JSONException, IOException {
		return new JSONObject(new Scanner(in, "UTF-8").useDelimiter("\\A").next());
	}

	public static BufferedImage scaleImage(BufferedImage image, int width, int height) {
		BufferedImage ret = new BufferedImage(width, height, image.getType());
		Graphics2D g = ret.createGraphics();
		g.drawImage(image, 0, 0, width, height, null);
		g.dispose();
		return ret;
	}

	public static <T> void sortWithNonTransitiveComparator(T[] array, Comparator<T> comparator) {
		@SuppressWarnings("unchecked")
		T[] tmp = (T[]) new Object[array.length];
		sortWithNonTransitiveComparator_MergeSort(array, comparator, tmp, 0, array.length - 1);
	}

	private static <T> void sortWithNonTransitiveComparator_Merge(T[] a, Comparator<T> comparator, T[] tmp, int left,
			int right, int rightEnd) {
		int leftEnd = right - 1;
		int k = left;
		int num = rightEnd - left + 1;

		while (left <= leftEnd && right <= rightEnd)
			if (comparator.compare(a[left], a[right]) <= 0)
				tmp[k++] = a[left++];
			else
				tmp[k++] = a[right++];

		while (left <= leftEnd) // Copy rest of first half
			tmp[k++] = a[left++];

		while (right <= rightEnd) // Copy rest of right half
			tmp[k++] = a[right++];

		// Copy tmp back
		for (int i = 0; i < num; i++, rightEnd--)
			a[rightEnd] = tmp[rightEnd];
	}

	private static <T> void sortWithNonTransitiveComparator_MergeSort(T[] a, Comparator<T> comparator, T[] tmp,
			int left, int right) {
		if (left < right) {
			int center = (left + right) / 2;
			sortWithNonTransitiveComparator_MergeSort(a, comparator, tmp, left, center);
			sortWithNonTransitiveComparator_MergeSort(a, comparator, tmp, center + 1, right);
			sortWithNonTransitiveComparator_Merge(a, comparator, tmp, left, center + 1, right);
		}
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

	public static Color withAlpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	private Utils() {
	}
}
