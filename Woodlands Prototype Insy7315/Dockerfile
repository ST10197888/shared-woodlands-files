FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY ["Woodlands Prototype Insy7315.csproj", "./"]
RUN dotnet restore "Woodlands Prototype Insy7315.csproj"
COPY . .
RUN dotnet publish "Woodlands Prototype Insy7315.csproj" -c Release -o /app/publish /p:UseAppHost=false
RUN ls -la /app/publish/wwwroot/downloads
RUN cp -r /app/publish/wwwroot/Images /app/publish/wwwroot/images

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS final
WORKDIR /app
COPY --from=build /app/publish .
ENV ASPNETCORE_URLS=http://+:8080
EXPOSE 8080
ENTRYPOINT ["dotnet", "Woodlands Prototype Insy7315.dll"]