/*
 * Copyright 2015-2025 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.params.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.params.converter.DefaultArgumentConverter.DEFAULT_LOCALE_CONVERSION_FORMAT_PROPERTY_NAME;
import static org.junit.jupiter.params.converter.DefaultArgumentConverter.LocaleConversionFormat.BCP_47;
import static org.junit.jupiter.params.converter.DefaultArgumentConverter.LocaleConversionFormat.ISO_639;
import static org.junit.platform.commons.util.ClassLoaderUtils.getClassLoader;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.commons.support.conversion.ConversionException;
import org.junit.platform.commons.test.TestClassLoader;
import org.junit.platform.commons.util.ClassLoaderUtils;

/**
 * Unit tests for {@link DefaultArgumentConverter}.
 *
 * @since 5.0
 */
class DefaultArgumentConverterTests {

	private final ExtensionContext context = mock();
	private final DefaultArgumentConverter underTest = spy(new DefaultArgumentConverter(context));

	@Test
	void isAwareOfNull() {
		assertConverts(null, Object.class, null);
		assertConverts(null, String.class, null);
		assertConverts(null, Boolean.class, null);
	}

	@Test
	void isAwareOfWrapperTypesForPrimitiveTypes() {
		assertConverts(true, boolean.class, true);
		assertConverts(false, boolean.class, false);
		assertConverts((byte) 1, byte.class, (byte) 1);
		assertConverts('o', char.class, 'o');
		assertConverts((short) 1, short.class, (short) 1);
		assertConverts(1, int.class, 1);
		assertConverts(1L, long.class, 1L);
		assertConverts(1.0f, float.class, 1.0f);
		assertConverts(1.0d, double.class, 1.0d);
	}

	@Test
	void isAwareOfWideningConversions() {
		assertConverts((byte) 1, short.class, (byte) 1);
		assertConverts((short) 1, int.class, (short) 1);
		assertConverts((char) 1, int.class, (char) 1);
		assertConverts((byte) 1, long.class, (byte) 1);
		assertConverts(1, long.class, 1);
		assertConverts((char) 1, float.class, (char) 1);
		assertConverts(1, float.class, 1);
		assertConverts(1L, double.class, 1L);
		assertConverts(1.0f, double.class, 1.0f);
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@ValueSource(classes = { char.class, boolean.class, short.class, byte.class, int.class, long.class, float.class,
			double.class, void.class })
	void throwsExceptionForNullToPrimitiveTypeConversion(Class<?> type) {
		assertThatExceptionOfType(ArgumentConversionException.class) //
				.isThrownBy(() -> convert(null, type)) //
				.withMessage("Cannot convert null to primitive value of type " + type.getCanonicalName());

		verify(underTest, never()).convert(any(), any(), any(ClassLoader.class));
	}

	@Test
	void throwsExceptionForNonStringsConversion() {
		assertThatExceptionOfType(ArgumentConversionException.class) //
				.isThrownBy(() -> convert(new Enigma(), String.class)) //
				.withMessage("No built-in converter for source type %s and target type java.lang.String",
					Enigma.class.getName());

		verify(underTest, never()).convert(any(), any(), any(ClassLoader.class));
	}

	@Test
	void delegatesStringsConversion() {
		doReturn(null).when(underTest).convert(any(), any(), any(ClassLoader.class));

		convert("value", int.class);

		verify(underTest).convert("value", int.class, getClassLoader(DefaultArgumentConverterTests.class));
	}

	@Test
	void convertsLocaleWithDefaultFormat() {
		when(context.getConfigurationParameter(eq(DEFAULT_LOCALE_CONVERSION_FORMAT_PROPERTY_NAME), any())) //
				.thenReturn(Optional.empty());

		assertConverts("en", Locale.class, Locale.ENGLISH);
		assertConverts("en-US", Locale.class, Locale.US);
	}

	@Test
	void convertsLocaleWithExplicitBcp47Format() {
		when(context.getConfigurationParameter(eq(DEFAULT_LOCALE_CONVERSION_FORMAT_PROPERTY_NAME), any())) //
				.thenReturn(Optional.of(BCP_47));

		assertConverts("en", Locale.class, Locale.ENGLISH);
		assertConverts("en-US", Locale.class, Locale.US);
	}

	@Test
	void delegatesLocaleConversionWithExplicitIso639Format() {
		when(context.getConfigurationParameter(eq(DEFAULT_LOCALE_CONVERSION_FORMAT_PROPERTY_NAME), any())) //
				.thenReturn(Optional.of(ISO_639));

		doReturn(null).when(underTest).convert(any(), any(), any(ClassLoader.class));

		convert("en", Locale.class);

		verify(underTest).convert("en", Locale.class, getClassLoader(DefaultArgumentConverterTests.class));
	}

	@Test
	void throwsExceptionForDelegatedConversionFailure() {
		ConversionException exception = new ConversionException("fail");
		doThrow(exception).when(underTest).convert(any(), any(), any(ClassLoader.class));

		assertThatExceptionOfType(ArgumentConversionException.class) //
				.isThrownBy(() -> convert("value", int.class)) //
				.withCause(exception) //
				.withMessage(exception.getMessage());

		verify(underTest).convert("value", int.class, getClassLoader(DefaultArgumentConverterTests.class));
	}

	@Test
	void delegatesStringToClassWithCustomTypeFromDifferentClassLoaderConversion() throws Exception {
		String customTypeName = Enigma.class.getName();
		try (var testClassLoader = TestClassLoader.forClasses(Enigma.class)) {
			var customType = testClassLoader.loadClass(customTypeName);
			assertThat(customType.getClassLoader()).isSameAs(testClassLoader);

			var declaringExecutable = ReflectionSupport.findMethod(customType, "foo").orElseThrow();
			assertThat(declaringExecutable.getDeclaringClass().getClassLoader()).isSameAs(testClassLoader);

			doReturn(customType).when(underTest).convert(any(), any(), any(ClassLoader.class));

			var clazz = (Class<?>) convert(customTypeName, Class.class, testClassLoader);
			assertThat(clazz).isNotEqualTo(Enigma.class);
			assertThat(clazz).isEqualTo(customType);
			assertThat(clazz.getClassLoader()).isSameAs(testClassLoader);

			verify(underTest).convert(customTypeName, Class.class, testClassLoader);
		}
	}

	// -------------------------------------------------------------------------

	private void assertConverts(Object input, Class<?> targetClass, Object expectedOutput) {
		var result = convert(input, targetClass);

		assertThat(result) //
				.describedAs(input + " --(" + targetClass.getName() + ")--> " + expectedOutput) //
				.isEqualTo(expectedOutput);

		verify(underTest, never()).convert(any(), any(), any(ClassLoader.class));
	}

	private Object convert(Object input, Class<?> targetClass) {
		return convert(input, targetClass, ClassLoaderUtils.getClassLoader(getClass()));
	}

	private Object convert(Object input, Class<?> targetClass, ClassLoader classLoader) {
		return underTest.convert(input, targetClass, classLoader);
	}

	@SuppressWarnings("unused")
	private static void foo() {
	}

	private static class Enigma {

		@SuppressWarnings("unused")
		void foo() {
		}
	}

}
