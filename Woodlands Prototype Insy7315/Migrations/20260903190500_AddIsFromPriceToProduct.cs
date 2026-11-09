using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Woodlands_Prototype_Insy7315.Migrations
{
    /// <inheritdoc />
    public partial class AddIsFromPriceToProduct : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "IsFromPrice",
                table: "Products",
                type: "INTEGER",
                nullable: false,
                defaultValue: false);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "IsFromPrice",
                table: "Products");
        }
    }
}
