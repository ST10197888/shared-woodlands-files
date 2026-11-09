// quoteNotifications.js - in-app notifications for quote (service) requests.
// Never throws: a notification problem must not break a quote request.

module.exports = function createQuoteNotifications(supabase, createNotification) {

    const escapeLike = (v) => String(v).replace(/[\\%_]/g, '\\$&');

    async function send(userIds, payload) {
        const unique = [...new Set(userIds.filter(Boolean))];
        const results = await Promise.allSettled(
            unique.map(user_id => createNotification({ user_id, ...payload }))
        );
        results.forEach(r => {
            if (r.status === 'rejected') {
                console.error('Quote notification failed:', r.reason?.message || r.reason);
            }
        });
    }

    // Active admins + active manager(s) of the quote's branch
    async function staffFor(branch) {
        const ids = [];

        const { data: admins, error: aErr } = await supabase
            .from('app_users').select('id')
            .eq('role', 'Admin').eq('active', true);
        if (aErr) console.error('Quote notification admin lookup failed:', aErr.message);
        (admins || []).forEach(u => ids.push(u.id));

        if (branch) {
            const { data: managers, error: mErr } = await supabase
                .from('app_users').select('id')
                .eq('role', `Manager (${branch})`)
                .eq('branch', branch).eq('active', true);
            if (mErr) console.error('Quote notification manager lookup failed:', mErr.message);
            (managers || []).forEach(u => ids.push(u.id));
        }
        return ids;
    }

    // A new quote: tell admins and that branch's manager(s)
    async function notifyNewQuote(quote) {
        try {
            if (!quote) return;
            const ids = await staffFor(quote.branch);
            if (ids.length === 0) return;

            const name = [quote.first_name, quote.last_name].filter(Boolean).join(' ') || 'A customer';
            const code = quote.quote_code || 'New quote';

            await send(ids, {
                type: 'quote_request_created',
                title: `New quote request ${code}`.slice(0, 120),
                message: `${name} requested a quote${quote.service ? ` for ${quote.service}` : ''}${quote.branch ? ` at ${quote.branch}` : ''}.`.slice(0, 1000),
                data: {
                    quote_id: quote.id,
                    quote_code: quote.quote_code || null,
                    branch: quote.branch || null,
                    service: quote.service || null
                }
            });
        } catch (e) {
            console.error('notifyNewQuote error:', e?.message || e);
        }
    }

    // Status changed: tell the customer, only if their email belongs to an account
    async function notifyQuoteStatus(quote, oldStatus, newStatus) {
        try {
            if (!quote?.email) return;

            const { data: users, error } = await supabase
                .from('app_users').select('id')
                .ilike('email', escapeLike(quote.email.trim()))
                .eq('active', true);
            if (error) {
                console.error('Quote notification customer lookup failed:', error.message);
                return;
            }
            if (!users || users.length === 0) return; // guest, no account

            const code = quote.quote_code || 'your quote';

            await send(users.map(u => u.id), {
                type: 'quote_request_status',
                title: 'Quote status updated',
                message: `Your quote ${code} changed from ${oldStatus || 'Pending'} to ${newStatus}.`.slice(0, 1000),
                data: {
                    quote_id: quote.id,
                    quote_code: quote.quote_code || null,
                    old_status: oldStatus || null,
                    new_status: newStatus
                }
            });
        } catch (e) {
            console.error('notifyQuoteStatus error:', e?.message || e);
        }
    }

    return { notifyNewQuote, notifyQuoteStatus };
};