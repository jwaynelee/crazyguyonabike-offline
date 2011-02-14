package photosintext;

import org.eclipse.swt.dnd.ByteArrayTransfer;
import org.eclipse.swt.dnd.TransferData;

/**
 * Used to transfer Thumbnails within the same VM (usually same widget).
 */
class MyTransfer extends ByteArrayTransfer {

	private static final MyTransfer INSTANCE = new MyTransfer();

	private static final String TYPE_NAME = "my-transfer-format" + (new Long(System.currentTimeMillis())).toString(); //$NON-NLS-1$;

	private static final int TYPEID = registerType(TYPE_NAME);

	public static MyTransfer getInstance() {
		return INSTANCE;
	}

	private Object selectedPhoto;

	public Object getSelectedPhoto() {
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

	public void setSelectedPhoto(Object selectedPhoto) {
		this.selectedPhoto = selectedPhoto;
	}
}