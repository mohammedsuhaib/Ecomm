/**
 * A schema.org block for the page it sits in.
 *
 * <p>Server component: the markup is for crawlers, and shipping a JSON blob
 * plus a component to the browser to build it there would be paying twice for
 * something no visitor ever sees.
 *
 * <p>`JSON.stringify` output is escaped before it goes into the script tag.
 * The data comes from our own API, but product names and descriptions are
 * typed by staff in the admin app, and a `</script>` in a product description
 * would otherwise end the block and put the rest of the JSON into the page as
 * live markup. Escaping the `<` is the standard defence and costs nothing.
 */
export default function JsonLd({ data }: { data: Record<string, unknown> }) {
  const json = JSON.stringify(data).replace(/</g, '\\u003c');
  return (
    <script
      type="application/ld+json"
      // eslint-disable-next-line react/no-danger -- the only way to emit a
      // JSON-LD block; the value is our own object, serialised and escaped above.
      dangerouslySetInnerHTML={{ __html: json }}
    />
  );
}
