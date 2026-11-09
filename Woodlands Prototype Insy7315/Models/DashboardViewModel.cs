using Woodlands_Prototype_Insy7315.Data;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class DashboardOverviewViewModel
    {
        public bool IsAdmin { get; set; }
        public bool IsManager { get; set; }
        public string? ManagerBranch { get; set; }
        public string DisplayName { get; set; } = "";

        // Admin overview
        public int TotalQuotes { get; set; }
        public int TotalCustomers { get; set; }
        public int CompletedJobs { get; set; }
        public double CompletionRate { get; set; }
        public string MonthlyRevenue { get; set; } = "";
        public List<BranchSummary> Branches { get; set; } = new();
        public List<MockQuote> RecentQuotes { get; set; } = new();

        // Manager overview
        public int PendingCount { get; set; }
        public int InProgressCount { get; set; }
        public int CompletedCount { get; set; }
        public string WeekRevenue { get; set; } = "";
        public List<int> WeeklyRevenueDays { get; set; } = new();
        public List<MockQuote> QuotesNeedingAttention { get; set; } = new();
    }

    public class BranchSummary
    {
        public string Name { get; set; } = "";
        public int Quotes { get; set; }
        public string Revenue { get; set; } = "";
        public int DonePercent { get; set; }
    }

    public class BranchCustomerViewModel
    {
        public string Name { get; set; } = "";
        public string Phone { get; set; } = "";
        public int QuoteCount { get; set; }
        public string LastQuoteDate { get; set; } = "";
    }
}