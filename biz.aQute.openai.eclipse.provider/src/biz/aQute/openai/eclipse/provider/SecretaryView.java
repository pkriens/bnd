package biz.aQute.openai.eclipse.provider;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

public class SecretaryView extends ViewPart {
	public static final String	ID	= "my.view.id";

	private SourceViewer		viewer;
	private Text				inputText;

	@Override
	public void createPartControl(Composite parent) {
		ScopedPreferenceStore store = new ScopedPreferenceStore(InstanceScope.INSTANCE,
				ID);
		JavaTextTools tools = new JavaTextTools(store);

		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));

		viewer = new SourceViewer(composite, null, null, false, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		viewer.getTextWidget().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		viewer.setEditable(true);
		viewer.getControl().setFont(JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT));
		Document document = new Document();
		tools.setupJavaDocumentPartitioner(document);
		viewer.setDocument(document);
		viewer.configure(new JavaSourceViewerConfiguration(tools.getColorManager(), store, null,
				IJavaPartitions.JAVA_PARTITIONING));

		inputText = new Text(composite, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		inputText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		Button sendButton = new Button(composite, SWT.PUSH);
		sendButton.setText("Send");
		sendButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
	}

	@Override
	public void setFocus() {
	}

}