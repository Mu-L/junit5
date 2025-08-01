/*
 * Copyright 2015-2025 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.commons.util.pkg1;

import org.junit.jupiter.api.BeforeAll;

/**
 * @see https://github.com/junit-team/junit-framework/issues/3553
 */
public class SuperclassWithStaticPackagePrivateBeforeMethod {

	@BeforeAll
	static void before() {
		// no-op
	}

}
