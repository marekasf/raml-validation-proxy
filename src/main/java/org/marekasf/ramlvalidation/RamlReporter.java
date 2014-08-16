package org.marekasf.ramlvalidation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public class RamlReporter extends Verticle
{
	private static final String[] defaultIgnored = {"request.header.Host", "response.header.Date", "request.header.Accept",
			"request.header.Connection", "response.header.Server", "request.header.Content-Length",
			"response.header.Content-Length"};

	private List<String> ignored;

	@Override
	public void start()
	{
		ignored = container.config().getArray("ignored.resources", new JsonArray(defaultIgnored)).toList();

		vertx.eventBus().registerHandler("raml_report", this::report);
	}

	private void report(final Message<JsonObject> message)
	{
		vertx.eventBus().send("raml_log", true, (Message<JsonObject> ramlMsg) -> {
			vertx.eventBus().send("proxy_log", true, (Message<JsonObject> proxyMsg) -> {
				final JsonObject ret = new JsonObject();
				report(ret, ramlMsg.body(), proxyMsg.body());
				message.reply(ret);
			});
		});
	}

	private Set<String> getKeys(final JsonObject raml, final JsonObject proxy)
	{
		final Set<String> uris = new HashSet<>();
		if (raml != null)
		{
			uris.addAll(raml.getFieldNames());
		}
		if (proxy != null)
		{
			uris.addAll(proxy.getFieldNames());
		}
		return uris;
	}

	private void report(final JsonObject ret, final JsonObject raml, final JsonObject proxy)
	{
		final Set<String> keys = getKeys(raml, proxy);

		keys.forEach(key -> {

			if (ignored.contains(key))
			{
				return;
			}
			else if (raml == null || !raml.containsField(key))
			{
				ret.putString(key, "NOT_DEFINED_IN_RAML");
			}
			else if (proxy == null || !proxy.containsField(key))
			{
				ret.putString(key, "NOT_CALLED_IN_TESTS");
			}
			else
			{
				final Object nestedRaml = raml == null ? null : raml.getValue(key);
				final Object nestedProxy = proxy == null ? null : proxy.getValue(key);

				if (nestedRaml == null && nestedProxy == null)
				{
					return;
				}

				if ((nestedRaml != null && !(nestedRaml instanceof JsonObject)) || (nestedProxy != null && !(nestedProxy
						instanceof JsonObject)))
				{
					// FIXME process values
					return;
				}

				final JsonObject parameters = get(ret, key);

				report(parameters, (JsonObject) nestedRaml, (JsonObject) nestedProxy);
			}
		});
	}

	protected JsonObject get(final JsonObject parent, final String key)
	{
		if (!parent.containsField(key))
		{
			parent.putObject(key, new JsonObject());
		}

		return parent.getObject(key);
	}
}
