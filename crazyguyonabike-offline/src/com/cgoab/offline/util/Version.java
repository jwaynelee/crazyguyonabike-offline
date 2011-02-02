package com.cgoab.offline.util;

/**
 * Represents a version, made up of up to 4 positive numeric parts.
 * 
 * When contstructed with less than 4 pats the unspecified parts will match
 * anything. For example "1" will consider itself
 * {@link #isGreaterThanOrEqual(Version)} to "1.1.1.1" (however a direct
 * {@link #equals(Object)} test reports false).
 */
public class Version {

	private static final int ANY = -1;

	private int major, minor, rev, patch;

	/**
	 * Matches up to four part dot separated version string.
	 * 
	 * @param version
	 *            version string to match (<tt>"2"</tt>, <tt>"2.2"</tt>,
	 *            <tt>"2.1.3"</tt> or <tt>"2.1.3.1"</tt>), extra characters must
	 *            be trimmed before calling.
	 * @return
	 */
	public static Version parse(String version) {
		String[] parts = version.split("\\.");
		int major = Integer.parseInt(parts[0]);
		int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : ANY;
		int rev = parts.length > 2 ? Integer.parseInt(parts[2]) : ANY;
		int patch = parts.length > 3 ? Integer.parseInt(parts[3]) : ANY;
		return new Version(major, minor, rev, patch, true);
	}

	public Version(int major) {
		Assert.isTrue(major >= 0);
		init(major, ANY, ANY, ANY);
	}

	public Version(int major, int minor) {
		Assert.isTrue(major >= 0);
		Assert.isTrue(minor >= 0);
		init(major, minor, ANY, ANY);
	}

	public Version(int major, int minor, int rev) {
		Assert.isTrue(major >= 0);
		Assert.isTrue(minor >= 0);
		Assert.isTrue(rev >= 0);
		init(major, minor, rev, ANY);
	}

	public Version(int major, int minor, int rev, int patch) {
		Assert.isTrue(major >= 0);
		Assert.isTrue(minor >= 0);
		Assert.isTrue(rev >= 0);
		Assert.isTrue(patch >= 0);
		init(major, minor, rev, patch);
	}

	private Version(int major, int minor, int rev, int patch, boolean internal) {
		init(major, minor, rev, patch);
	}

	private void init(int major, int minor, int rev, int patch) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.rev = rev;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Version)) {
			return false;
		}
		Version other = (Version) obj;
		return major == other.major && minor == other.minor && patch == other.patch && rev == other.rev;
	}

	/**
	 * Returns <tt>true</tt> if the passed in version is <i>older</i> than this
	 * version (equal to inverse of {@link #isGreaterThanOrEqual(Version)}).
	 * 
	 * @param other
	 * @return
	 */
	public boolean isLessThan(Version other) {
		return !isGreaterThanOrEqual(other);
	}

	/**
	 * Returns <tt>true</tt> if this version is <i>newer or equal</i> than the
	 * passed in version.
	 * 
	 * <pre>
	 * new Verion(1).isNewerOrEqual(new Version(2)); // false
	 * new Verion(1, 0).isNewerOrEqual(new Version(1, 0)); // true
	 * new Verion(2, 1).isNewerOrEqual(new Version(1, 2)); // true
	 * </pre>
	 * 
	 * @param other
	 * @return
	 */
	public boolean isGreaterThanOrEqual(Version other) {
		if (major < other.major) {
			return false;
		} else if (major > other.major) {
			return true;
		} else if (minor == ANY) {
			return true;
		}

		if (minor < other.minor) {
			return false;
		} else if (minor > other.minor) {
			return true;
		} else if (rev == ANY) {
			return true;
		}

		if (rev < other.rev) {
			return false;
		} else if (rev > other.rev) {
			return true;
		} else if (patch == ANY) {
			return true;
		}

		if (patch < other.patch) {
			return false;
		} else if (patch > other.patch) {
			return true;
		}

		/* all equal */
		return true;
	}

	@Override
	public String toString() {
		if (minor == ANY) {
			return String.format("%d", major);
		} else if (rev == ANY) {
			return String.format("%d.%d", major, minor);
		} else if (patch == ANY) {
			return String.format("%d.%d.%d", major, minor, rev);
		} else {
			return String.format("%d.%d.%d-%d", major, minor, rev, patch);
		}
	}
}