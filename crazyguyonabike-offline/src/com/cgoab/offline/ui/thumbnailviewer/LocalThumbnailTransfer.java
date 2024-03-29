package com.cgoab.offline.ui.thumbnailviewer;

import org.eclipse.swt.dnd.ByteArrayTransfer;
import org.eclipse.swt.dnd.TransferData;

/**
 * Used to transfer Thumbnails within the same VM (usually same widget).
 */
public class LocalThumbnailTransfer extends ByteArrayTransfer {

	private static final LocalThumbnailTransfer INSTANCE = new LocalThumbnailTransfer();

	private static final String TYPE_NAME = "local-photo-transfer-format" + (new Long(System.currentTimeMillis())).toString(); //$NON-NLS-1$;

	private static final int TYPEID = registerType(TYPE_NAME);

	public static LocalThumbnailTransfer getInstance() {
		return INSTANCE;
	}

	private ThumbnailHolder selectedPhoto;

	public ThumbnailHolder getSelectedPhoto() {
		return selectedPhoto;
	}

	@Override
	protected int[] getTypeIds() {
		return new int[] { TYPEID };
	}

	@Override
	protected String[] getTypeNames() {
		return new String[] { TYPE_NAME };
	}

	private boolean isInvalidNativeType(Object result) {
		return !(result instanceof byte[]) || !TYPE_NAME.equals(new String((byte[]) result));
	}

	@Override
	public void javaToNative(Object object, TransferData transferData) {
		byte[] check = TYPE_NAME.getBytes();
		super.javaToNative(check, transferData);
	}

	@Override
	public Object nativeToJava(TransferData transferData) {
		Object result = super.nativeToJava(transferData);
		if (isInvalidNativeType(result)) {
			// TODO log?
		}
		return selectedPhoto;
	}

	public void setSelectedPhoto(ThumbnailHolder selectedPhoto) {
		this.selectedPhoto = selectedPhoto;
	}
}