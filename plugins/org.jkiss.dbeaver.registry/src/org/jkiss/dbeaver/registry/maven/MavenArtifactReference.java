/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2021 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.registry.maven;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.utils.CommonUtils;

/**
 * Maven artifact reference
 */
public class MavenArtifactReference implements IMavenIdentifier
{
    public static final String VERSION_PATTERN_RELEASE = "RELEASE";
    public static final String VERSION_PATTERN_LATEST = "LATEST";
    public static final String VERSION_PATTERN_SNAPSHOT = "SNAPSHOT";

    private static final String DEFAULT_MAVEN_VERSION = VERSION_PATTERN_RELEASE;

    @NotNull
    private final String groupId;
    @NotNull
    private final String artifactId;
    @NotNull
    private final String version;
    @NotNull
    private final String id;
    @Nullable
    private final String fallbackVersion;
    private boolean resolveOptionalDependencies;

    public MavenArtifactReference(@NotNull String groupId, @NotNull String artifactId, @Nullable String fallbackVersion, @NotNull String version) {
        this.groupId = CommonUtils.trim(groupId);
        this.artifactId = CommonUtils.trim(artifactId);
        this.version = CommonUtils.trim(version);
        this.fallbackVersion = CommonUtils.trim(fallbackVersion);
        this.id = makeId(this);
    }

    public MavenArtifactReference(String ref) {
        String mavenUri = ref;
        int divPos = mavenUri.indexOf('/');
        if (divPos >= 0) {
            mavenUri = mavenUri.substring(divPos + 1);
        }
        divPos = mavenUri.indexOf(':');
        if (divPos < 0) {
            // No artifact ID
            groupId = mavenUri;
            artifactId = mavenUri;
            fallbackVersion = null;
            version = DEFAULT_MAVEN_VERSION;
        } else {
            groupId = mavenUri.substring(0, divPos);
            int divPos2 = mavenUri.indexOf(':', divPos + 1);
            if (divPos2 < 0) {
                // No version
                artifactId = mavenUri.substring(divPos + 1);
                fallbackVersion = null;
                version = DEFAULT_MAVEN_VERSION;
            } else {
                int divPos3 = mavenUri.indexOf(':', divPos2 + 1);
                if (divPos3 < 0) {
                    // No classifier
                    artifactId = mavenUri.substring(divPos + 1, divPos2);
                    fallbackVersion = null;
                    version = mavenUri.substring(divPos2 + 1);
                } else {
                    artifactId = mavenUri.substring(divPos + 1, divPos2);
                    fallbackVersion = mavenUri.substring(divPos2 + 1, divPos3);
                    version = mavenUri.substring(divPos3 + 1);
                }
            }
        }
        id = makeId(this);
    }

    @Override
    @NotNull
    public String getGroupId() {
        return groupId;
    }

    @Override
    @NotNull
    public String getArtifactId() {
        return artifactId;
    }

    @Override
    @Nullable
    public String getFallbackVersion() {
        return fallbackVersion;
    }

    @Override
    @NotNull
    public String getVersion() {
        return version;
    }

    @Override
    @NotNull
    public String getId() {
        return id;
    }

    public String getPath() {
        return id + ":" + version;
    }

    public boolean isResolveOptionalDependencies() {
        return resolveOptionalDependencies;
    }

    public void setResolveOptionalDependencies(boolean resolveOptionalDependencies) {
        this.resolveOptionalDependencies = resolveOptionalDependencies;
    }

    @Override
    public String toString() {
        return getPath();
    }

    @Override
    public int hashCode() {
        return groupId.hashCode() + artifactId.hashCode() + version.hashCode();
    }

    static String makeId(IMavenIdentifier identifier) {
        if (identifier.getFallbackVersion() != null) {
            return identifier.getGroupId() + ":" + identifier.getArtifactId() + ":" + identifier.getFallbackVersion();
        } else {
            return identifier.getGroupId() + ":" + identifier.getArtifactId();
        }
    }

}
