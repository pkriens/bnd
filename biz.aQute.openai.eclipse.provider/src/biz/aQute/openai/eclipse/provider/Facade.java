package biz.aQute.openai.eclipse.provider;

import java.util.function.Supplier;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;

public class Facade implements IExecutableExtensionFactory {
	static Supplier<IJavaCompletionProposalComputer> service;

	@Override
	public Object create() throws CoreException {
		try {
			synchronized (Facade.class) {
				while (service == null)
					Facade.class.wait();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CoreException(Status.CANCEL_STATUS);
		}
		return service.get();
	}

}

