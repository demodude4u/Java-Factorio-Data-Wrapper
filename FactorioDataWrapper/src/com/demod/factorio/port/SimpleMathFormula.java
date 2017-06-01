package com.demod.factorio.port;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * This is a port directly from factorio source code, for how formulas are
 * parsed and computed. Here is the source:
 * https://paste.openttdcoop.org/p2icfbosl
 */
public final class SimpleMathFormula {
	private static class Box<T> {
		T value;

		Box(T value) {
			this.value = value;
		}
	}

	public static abstract class Expression {
		private static void addToMultiplication(Box<Expression> item, Box<Expression> firstThing,
				Box<Expression> result) {
			if (result.value != null) {
				result.value.children.add(item.value);
			} else if (firstThing.value == null) {
				firstThing.value = item.value;
			} else {
				result.value = new Multiplication();
				result.value.children.add(firstThing.value);
				result.value.children.add(item.value);
			}
		}

		private static void finishExpression(String input, Box<Expression> firstThing, Box<Expression> result,
				Box<Integer> readingNumber, int position) {
			if (readingNumber.value != -1) {
				String substring = input.substring(readingNumber.value, position - 1);
				double theNumber = Double.parseDouble(substring);
				addToMultiplication(new Box<>(new Numeric(theNumber)), firstThing, result);
				readingNumber.value = -1;
			}
		}

		public static Expression parse(String input, int position) throws InputException {
			return parseInternal(input + " ", new Box<>(position)).value;
		}

		private static Box<Expression> parseInternal(String input, Box<Integer> position) throws InputException {
			if (input.isEmpty()) {
				throw new InputException("Expression can't be an empty string.");
			}

			Box<Expression> firstThing = parseMultiplication(input, position);
			if (input.length() <= position.value) {
				return firstThing;
			}
			if (input.charAt(position.value) == '+' || input.charAt(position.value) == '-') {
				boolean minus = input.charAt(position.value) == '-';
				position.value++;

				Box<Expression> result = new Box<>(new Summation());
				result.value.children.add(firstThing.value);
				do {
					Box<Expression> addition = parseMultiplication(input, position);
					if (minus) {
						addition.value = addition.value.multiplyByNumber(-1);
						minus = false;
					}
					result.value.children.add(addition.value);
					if (input.length() > position.value) {
						char ch = input.charAt(position.value);
						if (ch == '-') {
							minus = true;
							position.value++;
							continue;
						}
						if (ch == '+') {
							position.value++;
							continue;
						}
						if (ch == ')') {
							break;
						}
						throw new InputException(
								"Unexpected character in summation: '" + input.charAt(position.value) + "'");
					}
				} while (input.length() > position.value && input.charAt(position.value) != ')');

				return result;
			}

			return firstThing;
		}

		private static Box<Expression> parseMultiplication(String input, Box<Integer> position) throws InputException {
			// TODO determine if true or false
			return parseMultiplication(input, position, false);
		}

		private static Box<Expression> parseMultiplication(String input, Box<Integer> position, boolean onlyOnePart)
				throws InputException {
			Box<Expression> firstThing = new Box<>(null);
			Box<Expression> result = new Box<>(null);
			if (input.length() < position.value) {
				throw new InputException("The expression ended unexpectedly.");
			}
			Box<Integer> readingNumber = new Box<>(-1);
			do {
				char ch = input.charAt(position.value);
				position.value++;
				if (ch == ' ') {
					continue;
				}
				if (ch >= '0' && ch <= '9' || ch == '.') {
					if (readingNumber.value == -1) {
						readingNumber.value = position.value - 1;
					}
					continue;
				} else if (readingNumber.value != -1) {
					finishExpression(input, firstThing, result, readingNumber, position.value);
				}
				if (ch == '*') {
					if (position.value < 2 || input.charAt(position.value - 2) == '*'
							|| firstThing.value == null && result.value == null) {
						throw new InputException("Misplaced *");
					}
					continue;
				}
				if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z') {
					addToMultiplication(new Box<>(new Variable("" + ch)), firstThing, result);
					continue;
				}
				if (ch == '(') {
					Box<Expression> inner = parseInternal(input, position);
					if (input.length() <= position.value || input.charAt(position.value) != ')') {
						throw new InputException(") expected");
					}
					position.value++;
					addToMultiplication(inner, firstThing, result);
					continue;
				}
				if (ch == ')') {
					position.value--;
					break;
				}
				if (ch == '^') {
					Box<Expression> base = new Box<>(null);
					if (result.value == null) {
						if (firstThing.value == null) {
							throw new InputException("Misplaced ^");
						}
						base.value = firstThing.value;
						firstThing.value = null;
					} else {
						base = new Box<>(result.value.children.get(result.value.children.size() - 1));
						result.value.children.remove(result.value.children.size() - 1);
					}
					Box<Expression> exponent = parseMultiplication(input, position, true);
					addToMultiplication(new Box<>(new Pow(base.value, exponent.value)), firstThing, result);
					continue;
				}
				if (ch == '+' || ch == '-') {
					position.value--;
					break;
				}
				throw new InputException("Unknown character + '" + ch + "'");
			} while (input.length() > position.value && (!onlyOnePart || firstThing.value == null));
			finishExpression(input, firstThing, result, readingNumber, position.value);
			if (result.value != null) {
				return result;
			}
			if (firstThing.value != null) {
				return firstThing;
			}
			throw new InputException("Empty expression in '" + input + "'");
		}

		protected List<Expression> children = new ArrayList<>();

		public abstract double evaluate(Map<String, Double> values);

		public Expression multiplyByNumber(double number) {
			Multiplication result = new Multiplication();
			result.children.add(this);
			result.children.add(new Numeric(number));
			return result;
		}
	}

	@SuppressWarnings("serial")
	public static class InputException extends Exception {
		public InputException(String message) {
			super(message);
		}
	}

	public static class Multiplication extends Expression {
		@Override
		public double evaluate(Map<String, Double> values) {
			double result = 1;
			for (Expression item : children) {
				result *= item.evaluate(values);
			}
			return result;
		}

		@Override
		public Expression multiplyByNumber(double number) {
			children.add(new Numeric(number));
			return this;
		}
	}

	public static class Numeric extends Expression {
		private double value;

		public Numeric(double value) {
			this.value = value;
		}

		@Override
		public double evaluate(Map<String, Double> values) {
			return value;
		}

		@Override
		public Expression multiplyByNumber(double number) {
			value *= number;
			return this;
		}
	}

	public static class Pow extends Expression {
		private final Expression base;
		private final Expression exponent;

		public Pow(Expression base, Expression exponent) {
			this.base = base;
			this.exponent = exponent;
		}

		@Override
		public double evaluate(Map<String, Double> values) {
			return Math.pow(base.evaluate(values), exponent.evaluate(values));
		}
	}

	public static class Summation extends Expression {
		@Override
		public double evaluate(Map<String, Double> values) {
			double result = 0;
			for (Expression item : children) {
				result += item.evaluate(values);
			}
			return result;
		}
	}

	public static class Variable extends Expression {
		private final String name;

		public Variable(String name) {
			this.name = name;
		}

		@Override
		public double evaluate(Map<String, Double> values) {
			Double result = values.get(name);
			if (result == null) {
				throw new IllegalArgumentException("The value of variable " + name + " was not specified");
			}
			return result;
		}
	}

	public static void main(String[] args) throws InputException {
		System.out.println(Expression.parse("2^(L-8)*1000", 0).evaluate(ImmutableMap.of("L", 8.0)));
	}

	private SimpleMathFormula() {
	}
}
