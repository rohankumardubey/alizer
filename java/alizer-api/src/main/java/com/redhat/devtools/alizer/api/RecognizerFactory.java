/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.alizer.api;

public class RecognizerFactory {
    public LanguageRecognizer createLanguageRecognizer() {
        return new LanguageRecognizerImpl(this);
    }

    public DevFileRecognizer createDevFileRecognizer() {
        return new DevFileRecognizerImpl(this);
    }

    public ComponentRecognizer createComponentRecognizer() {
        return new ComponentRecognizerImpl(this);
    }
}
