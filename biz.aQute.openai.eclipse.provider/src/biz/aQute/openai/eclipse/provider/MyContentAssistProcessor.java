package biz.aQute.openai.eclipse.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.osgi.dto.DTO;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import aQute.bnd.build.Workspace;
import aQute.bnd.exceptions.Exceptions;
import aQute.lib.json.JSONCodec;

@Component
public class MyContentAssistProcessor {
	@Reference
	Workspace			ws;
	static JSONCodec	codec	= new JSONCodec();

	public static class CompletionRq extends DTO {
		public String	model;
		public String	prompt;
		public int		max_tokens;
		public int		n;
		public double	temperature;
	}

	public static class Choice extends DTO {
		public String	text;
		public int		index;
		public Object	logprobs;
		public String	finish_reason;
	}

	public static class Usage extends DTO {
		public int	prompt_tokens;
		public int	completion_tokens;
		public int	total_tokens;
	}

	public static class CompletionRsp extends DTO {
		public String	id;
		public String	object;
		public long		created;
		public String	model;
		public Choice[]	choices;
		public Usage[]	usage;
	}

	class Assist implements IJavaCompletionProposalComputer {
		Assist() {
			System.out.println("constructor");
		}

		@Override
		public void sessionStarted() {
			System.out.println("session started");
		}

		@Override
		public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext context,
				IProgressMonitor monitor) {

			try {
				IDocument document = context.getDocument();
				String string = document.get();
				int n = context.getInvocationOffset();
				string = string.substring(0, n) + "|" + string.substring(n);
				System.out.println("complete " + string);

				CompletionRq crq = new CompletionRq();
				crq.max_tokens = 1000;
				crq.model = "text-davinci-003";
				crq.n = 3;
				crq.temperature = 0.2d;

				String s = codec.enc().put(crq).toString();
				

				HttpClient client = HttpClient.newBuilder()
						.version(Version.HTTP_1_1)
						.connectTimeout(Duration.ofSeconds(20))
						.build();

				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create("https://api.openai.com/v1/completions"))
						.header("Authorization", "Bearer " + "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")
						.timeout(Duration.ofMinutes(2))
						.header("Content-Type", "application/json")
						.POST(BodyPublishers.ofString(s))
						.build();

				CompletableFuture<HttpResponse<String>> f = client.sendAsync(request, BodyHandlers.ofString());

				List<ICompletionProposal> l = new ArrayList<>();

				ICompletionProposal c = new ICompletionProposal() {

					@Override
					public void apply(IDocument document) {
						try {
							HttpResponse<String> response = f.get();
							if (response.statusCode() != 200) {
								System.out.println("sorry, failure");
								return;
							}

							String body = response.body();
							System.out.println("body " + body);
							CompletionRsp resp = codec.dec().from(body).get(CompletionRsp.class);
							System.out.println("proposal " + resp.choices[0].text);
							document.replace(context.getInvocationOffset(), context.getInvocationOffset(),
									resp.choices[0].text);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (ExecutionException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						// TODO Auto-generated method stub

					}

					@Override
					public Point getSelection(IDocument document) {
						return null;
					}

					@Override
					public String getAdditionalProposalInfo() {
						// TODO Auto-generated method stub
						return "Huh?";
					}

					@Override
					public String getDisplayString() {
						return "OpenAI";
					}

					@Override
					public Image getImage() {
						return null;
					}

					@Override
					public IContextInformation getContextInformation() {
						return null;
					}

				};
				l.add(c);

				return l;
			} catch (Exception e1) {
				throw Exceptions.duck(e1);				
			}

		}

		@Override
		public List<IContextInformation> computeContextInformation(ContentAssistInvocationContext context,
				IProgressMonitor monitor) {
			System.out.println("context");
			return Collections.emptyList();
		}

		@Override
		public String getErrorMessage() {
			return "Error messsage";
		}

		@Override
		public void sessionEnded() {
			System.out.println("session ended");

		}
	}

	@Activate
	public MyContentAssistProcessor() {
		synchronized (Facade.class) {
			Facade.service = Assist::new;
			Facade.class.notifyAll();
		}
	}
}
