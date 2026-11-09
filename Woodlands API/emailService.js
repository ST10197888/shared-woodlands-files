const formData = require('form-data');
const Mailgun = require('mailgun.js');

const mailgun = new Mailgun(formData);

function createMailgunClient() {
    const apiKey = process.env.MAILGUN_API_KEY;

    if (!apiKey) {
        return null;
    }

    const options = {
        username: 'api',
        key: apiKey
    };

    if (process.env.MAILGUN_REGION?.toLowerCase() === 'eu') {
        options.url = 'https://api.eu.mailgun.net';
    }

    return mailgun.client(options);
}

async function sendEmail({ to, subject, html, text }) {
    if (!process.env.MAILGUN_API_KEY) {
        console.warn('MAILGUN_API_KEY is not configured. Email was not sent.');
        return { success: false, skipped: true };
    }

    if (!process.env.MAILGUN_DOMAIN) {
        console.warn('MAILGUN_DOMAIN is not configured. Email was not sent.');
        return { success: false, skipped: true };
    }

    if (!process.env.MAILGUN_SENDER_EMAIL) {
        console.warn('MAILGUN_SENDER_EMAIL is not configured. Email was not sent.');
        return { success: false, skipped: true };
    }

    if (!to) {
        console.warn('No recipient email supplied. Email was not sent.');
        return { success: false, skipped: true };
    }

    const recipients = Array.isArray(to)
        ? to
            .filter(email => typeof email === 'string' && email.trim())
            .map(email => email.trim())
        : [String(to).trim()];

    if (recipients.length === 0) {
        console.warn('No valid recipient emails supplied.');
        return { success: false, skipped: true };
    }

    const mg = createMailgunClient();

    if (!mg) {
        return { success: false, skipped: true };
    }

    try {
        const result = await mg.messages.create(
            process.env.MAILGUN_DOMAIN,
            {
                from: `${process.env.MAILGUN_SENDER_NAME || 'Woodlands Designer Boards'} <${process.env.MAILGUN_SENDER_EMAIL}>`,
                to: recipients,
                subject,
                text: text || stripHtml(html || ''),
                html
            }
        );

        console.log('Mailgun email sent:', result);

        return {
            success: true,
            messageId: result.id || null
        };
    } catch (error) {
        console.error('Mailgun email failed:', error);

        return {
            success: false,
            error: error.message
        };
    }
}

async function getAdminEmails(supabase) {
    const { data, error } = await supabase
        .from('app_users')
        .select('email')
        .eq('role', 'Admin')
        .eq('active', true);

    if (error) {
        console.error('Failed to find admin recipients:', error);
        return [];
    }

    return (data || [])
        .map(user => user.email?.trim())
        .filter(Boolean);
}

async function getBranchManagerEmails(supabase, branch) {
    if (!branch) {
        return [];
    }

    const managerRole = `Manager (${branch})`;

    const { data, error } = await supabase
        .from('app_users')
        .select('email')
        .eq('role', managerRole)
        .eq('branch', branch)
        .eq('active', true);

    if (error) {
        console.error('Failed to find branch manager recipients:', error);
        return [];
    }

    return (data || [])
        .map(user => user.email?.trim())
        .filter(Boolean);
}

async function sendCustomerQuoteReceivedEmail(quote) {
    if (!quote?.email) {
        return { success: false, skipped: true };
    }

    return sendEmail({
        to: quote.email,
        subject: `Quote Request Received - ${quote.quote_code || 'Woodlands Designer Boards'}`,
        html: `
            <h2>Quote Request Received</h2>

            <p>Hello ${escapeHtml(quote.first_name || 'Customer')},</p>

            <p>
                Thank you for requesting a quote from
                <strong>Woodlands Designer Boards</strong>.
            </p>

            <p>
                We have received your request and our team will review it shortly.
            </p>

            <p>
                <strong>Quote reference:</strong>
                ${escapeHtml(quote.quote_code || 'Pending')}
            </p>

            <p>
                <strong>Branch:</strong>
                ${escapeHtml(quote.branch || 'Not specified')}
            </p>

            <p>
                <strong>Status:</strong>
                ${escapeHtml(quote.status || 'Pending')}
            </p>

            <p>
                Thank you,<br>
                Woodlands Designer Boards
            </p>
        `
    });
}

async function sendAdminNewQuoteEmail(supabase, quote) {
    const adminEmails = await getAdminEmails(supabase);

    if (adminEmails.length === 0) {
        console.warn('No active Admin users found for quote notification.');
        return { success: false, skipped: true };
    }

    return sendEmail({
        to: adminEmails,
        subject: `New Quote Request - ${quote.quote_code || 'Woodlands Designer Boards'}`,
        html: `
            <h2>New Quote Request</h2>

            <p>A new quote request has been submitted.</p>

            <p>
                <strong>Quote reference:</strong>
                ${escapeHtml(quote.quote_code || 'Pending')}
            </p>

            <p>
                <strong>Customer:</strong>
                ${escapeHtml(
                    `${quote.first_name || ''} ${quote.last_name || ''}`.trim() || 'Not provided'
                )}
            </p>

            <p>
                <strong>Email:</strong>
                ${escapeHtml(quote.email || 'Not provided')}
            </p>

            <p>
                <strong>Phone:</strong>
                ${escapeHtml(quote.phone || 'Not provided')}
            </p>

            <p>
                <strong>Branch:</strong>
                ${escapeHtml(quote.branch || 'Not specified')}
            </p>

            <p>
                <strong>Service:</strong>
                ${escapeHtml(quote.service || 'Not specified')}
            </p>

            <p>
                <strong>Message:</strong><br>
                ${escapeHtml(quote.message || 'No message provided')}
            </p>
        `
    });
}

async function sendBranchManagerNewQuoteEmail(supabase, quote) {
    const managerEmails = await getBranchManagerEmails(
        supabase,
        quote.branch
    );

    if (managerEmails.length === 0) {
        console.warn(
            `No active manager found for branch "${quote.branch}".`
        );

        return { success: false, skipped: true };
    }

    return sendEmail({
        to: managerEmails,
        subject: `New ${quote.branch} Quote - ${quote.quote_code || 'Woodlands Designer Boards'}`,
        html: `
            <h2>New Quote Request</h2>

            <p>
                A new quote request has been submitted for
                <strong>${escapeHtml(quote.branch || 'your branch')}</strong>.
            </p>

            <p>
                <strong>Quote reference:</strong>
                ${escapeHtml(quote.quote_code || 'Pending')}
            </p>

            <p>
                <strong>Customer:</strong>
                ${escapeHtml(
                    `${quote.first_name || ''} ${quote.last_name || ''}`.trim() || 'Not provided'
                )}
            </p>

            <p>
                <strong>Email:</strong>
                ${escapeHtml(quote.email || 'Not provided')}
            </p>

            <p>
                <strong>Phone:</strong>
                ${escapeHtml(quote.phone || 'Not provided')}
            </p>

            <p>
                <strong>Service:</strong>
                ${escapeHtml(quote.service || 'Not specified')}
            </p>

            <p>
                <strong>Message:</strong><br>
                ${escapeHtml(quote.message || 'No message provided')}
            </p>
        `
    });
}

async function sendCustomerStatusEmail(quote, oldStatus, newStatus) {
    if (!quote?.email) {
        return { success: false, skipped: true };
    }

    return sendEmail({
        to: quote.email,
        subject: `Quote Update - ${quote.quote_code || 'Woodlands Designer Boards'}`,
        html: `
            <h2>Your Quote Has Been Updated</h2>

            <p>Hello ${escapeHtml(quote.first_name || 'Customer')},</p>

            <p>
                Your quote request has been updated.
            </p>

            <p>
                <strong>Quote reference:</strong>
                ${escapeHtml(quote.quote_code || 'Pending')}
            </p>

            <p>
                <strong>Previous status:</strong>
                ${escapeHtml(oldStatus || 'Unknown')}
            </p>

            <p>
                <strong>New status:</strong>
                ${escapeHtml(newStatus || 'Unknown')}
            </p>

            <p>
                Thank you,<br>
                Woodlands Designer Boards
            </p>
        `
    });
}

async function sendWelcomeEmail(user) {
    if (!user?.email) {
        return { success: false, skipped: true };
    }

    return sendEmail({
        to: user.email,
        subject: 'Welcome to Woodlands Designer Boards',
        html: `
            <h2>Welcome to Woodlands Designer Boards</h2>

            <p>
                Hello ${escapeHtml(user.full_name || 'Customer')},
            </p>

            <p>
                Your account has been successfully created.
            </p>

            <p>
                You can now use your Woodlands Designer Boards account
                to request quotes and access your account.
            </p>

            <p>
                Thank you,<br>
                Woodlands Designer Boards
            </p>
        `
    });
}

async function sendAdminNewRegistrationEmail(supabase, user) {
    const adminEmails = await getAdminEmails(supabase);

    if (adminEmails.length === 0) {
        console.warn(
            'No active Admin users found for registration notification.'
        );

        return { success: false, skipped: true };
    }

    return sendEmail({
        to: adminEmails,
        subject: 'New Customer Registration',
        html: `
            <h2>New Customer Registration</h2>

            <p>A new customer account has been registered.</p>

            <p>
                <strong>Name:</strong>
                ${escapeHtml(user.full_name || 'Not provided')}
            </p>

            <p>
                <strong>Email:</strong>
                ${escapeHtml(user.email || 'Not provided')}
            </p>

            <p>
                <strong>Phone:</strong>
                ${escapeHtml(user.phone || 'Not provided')}
            </p>

            <p>
                <strong>Role:</strong>
                ${escapeHtml(user.role || 'Customer')}
            </p>
        `
    });
}

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function stripHtml(html) {
    return String(html || '')
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<\/p>/gi, '\n\n')
        .replace(/<[^>]*>/g, '')
        .trim();
}

async function sendRestoreCodeEmail(to, code, minutes) {
    return sendEmail({
        to,
        subject: 'Your Woodlands account restore code',
        html: `
            <h2>Account restore code</h2>
            <p>Use this code to restore your Woodlands Designer Boards account:</p>
            <p style="font-size:24px;font-weight:bold;letter-spacing:4px;">${escapeHtml(String(code))}</p>
            <p>The code expires in ${escapeHtml(String(minutes))} minutes. If you did not request it, you can ignore this email.</p>
        `
    });
}

module.exports = {
    sendCustomerQuoteReceivedEmail,
    sendAdminNewQuoteEmail,
    sendBranchManagerNewQuoteEmail,
    sendCustomerStatusEmail,
    sendWelcomeEmail,
    sendAdminNewRegistrationEmail,
    sendRestoreCodeEmail
};